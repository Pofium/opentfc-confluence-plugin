package org.opentfc.service;

import org.opentfc.diag.OpenTfcLogStore;
import org.opentfc.model.CellModel;
import org.opentfc.model.ColumnModel;
import org.opentfc.model.ColumnType;
import org.opentfc.model.RowModel;
import org.opentfc.model.SqlExecutionResult;
import org.opentfc.model.TableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sandboxed in-memory SQL engine (DuckDB). Source tables are exposed as T1..Tn.
 * External file/network access is disabled and configuration is locked before the user query runs.
 */
public class SqlTransformerEngine {
    private static final Logger log = LoggerFactory.getLogger(SqlTransformerEngine.class);

    private static final Pattern ALLOWED_START = Pattern.compile(
            "^\\s*(\\(\\s*)*(SELECT|WITH|FROM|PIVOT|UNPIVOT|VALUES)\\b", Pattern.CASE_INSENSITIVE);
    /** Prototype dialect: T1.'Column Name' -> T1."Column Name". */
    private static final Pattern QUOTED_COLUMN = Pattern.compile("\\b(T\\d+)\\.'((?:[^']|'')*)'");
    private static final Pattern NUMBER = Pattern.compile("^[-+]?\\d+([.,]\\d+)?$");

    private final int timeoutSeconds;
    private final int maxResultRows;
    private static volatile boolean firstEngineUseLogged = false;

    public SqlTransformerEngine() {
        this(10, 50000);
    }

    public SqlTransformerEngine(int timeoutSeconds, int maxResultRows) {
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.maxResultRows = Math.max(1, maxResultRows);
    }

    /** Strip comments and string literals to inspect query commands safely. */
    public static String stripSqlNoise(String sql) {
        if (sql == null) return "";
        String s = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        s = s.replaceAll("--.*?(\\r?\\n|$)", " ");
        s = s.replaceAll("'(?:''|[^'])*'", "''");
        s = s.replaceAll("\"(?:\"\"|[^\"])*\"", "\"\"");
        return s;
    }

    /** Rewrite prototype-specific syntax into DuckDB SQL. */
    static String preprocess(String sql) {
        String s = sql.trim();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        Matcher m = QUOTED_COLUMN.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String col = m.group(2).replace("''", "'").replace("\"", "\"\"");
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + ".\"" + col + "\""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Execute a read-only query over the given source tables. */
    public SqlExecutionResult executeQuery(String sql, List<TableModel> sourceTables) {
        long start = System.currentTimeMillis();
        if (sql == null || sql.isBlank()) {
            return SqlExecutionResult.error("SQL query is empty", 0);
        }
        String query = preprocess(sql);
        if (!ALLOWED_START.matcher(query).find() || query.contains(";")) {
            return SqlExecutionResult.error("Security violation: only a single SELECT query is permitted", 0);
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "opentfc-duckdb");
            t.setDaemon(true);
            return t;
        });
        OpenTfcLogStore.info("sql-engine", "Executing query over " + sourceTables.size()
                + " source table(s), len=" + query.length());
        try (Connection conn = new org.duckdb.DuckDBDriver().connect("jdbc:duckdb:", new Properties())) {
            if (!firstEngineUseLogged) {
                firstEngineUseLogged = true;
                OpenTfcLogStore.info("sql-engine", "DuckDB engine loaded successfully (first query)");
            }
            for (int i = 0; i < sourceTables.size(); i++) {
                createAndPopulateTable(conn, "T" + (i + 1), sourceTables.get(i));
            }
            try (Statement st = conn.createStatement()) {
                st.execute("SET enable_external_access=false");
                st.execute("SET lock_configuration=true");
            }

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                Future<TableModel> future = executor.submit(() -> readResult(stmt));
                try {
                    TableModel table = future.get(timeoutSeconds, TimeUnit.SECONDS);
                    return SqlExecutionResult.success(table, System.currentTimeMillis() - start);
                } catch (TimeoutException te) {
                    stmt.cancel();
                    future.cancel(true);
                    return SqlExecutionResult.error("Query timeout (" + timeoutSeconds + " s) exceeded",
                            System.currentTimeMillis() - start);
                } catch (ExecutionException ee) {
                    Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                    throw cause instanceof SQLException ? (SQLException) cause : new SQLException(cause);
                }
            }
        } catch (SQLException e) {
            String code = "OTFC-SQL-" + Integer.toHexString(Math.abs(String.valueOf(e.getMessage()).hashCode()));
            log.warn("OpenTFC SQL error [{}]: {}", code, e.getMessage());
            OpenTfcLogStore.warn("sql-engine", "SQL error [" + code + "]: " + firstLine(e.getMessage()));
            return SqlExecutionResult.error(firstLine(e.getMessage()) + " (" + code + ")",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("OpenTFC unexpected SQL engine failure", e);
            OpenTfcLogStore.error("sql-engine", "Unexpected SQL engine failure", e);
            return SqlExecutionResult.error("Execution failed: " + e.getClass().getSimpleName(),
                    System.currentTimeMillis() - start);
        } finally {
            executor.shutdownNow();
        }
    }

    private TableModel readResult(PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            TableModel result = new TableModel(UUID.randomUUID().toString());
            for (int c = 1; c <= colCount; c++) {
                result.addColumn(new ColumnModel(c - 1, meta.getColumnLabel(c), mapJdbcType(meta.getColumnType(c))));
            }
            int rows = 0;
            while (rs.next() && rows < maxResultRows) {
                RowModel row = new RowModel();
                for (int c = 1; c <= colCount; c++) {
                    Object val = rs.getObject(c);
                    Object out = val;
                    if (val instanceof Double || val instanceof Float) {
                        double d = ((Number) val).doubleValue();
                        if (!Double.isInfinite(d) && !Double.isNaN(d) && Math.floor(d) == d
                                && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                            out = (long) d;
                        }
                    } else if (val != null && !(val instanceof Number) && !(val instanceof Boolean)) {
                        out = String.valueOf(val);
                    }
                    row.addCell(new CellModel(out, null, result.getColumns().get(c - 1).getType()));
                }
                result.addRow(row);
                rows++;
            }
            return result;
        }
    }

    private void createAndPopulateTable(Connection conn, String tableName, TableModel table) throws SQLException {
        List<ColumnModel> cols = table.getColumns();
        if (cols == null || cols.isEmpty()) {
            return;
        }
        boolean[] numeric = new boolean[cols.size()];
        for (int c = 0; c < cols.size(); c++) {
            numeric[c] = isNumericColumn(table, c);
        }

        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
        for (int c = 0; c < cols.size(); c++) {
            if (c > 0) {
                ddl.append(", ");
            }
            String name = cols.get(c).getName() == null ? "Column " + (c + 1) : cols.get(c).getName();
            ddl.append('"').append(name.replace("\"", "\"\"")).append("\" ")
               .append(numeric[c] ? "DOUBLE" : "VARCHAR");
        }
        ddl.append(')');
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(ddl.toString());
        }
        if (table.getRows() == null || table.getRows().isEmpty()) {
            return;
        }

        StringBuilder ins = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES (");
        for (int c = 0; c < cols.size(); c++) {
            ins.append(c > 0 ? ", ?" : "?");
        }
        ins.append(')');
        try (PreparedStatement ps = conn.prepareStatement(ins.toString())) {
            for (RowModel row : table.getRows()) {
                for (int c = 0; c < cols.size(); c++) {
                    String raw = cellText(row, c);
                    if (numeric[c]) {
                        if (raw.isEmpty()) {
                            ps.setNull(c + 1, Types.DOUBLE);
                        } else {
                            ps.setDouble(c + 1, Double.parseDouble(normalizeNumber(raw)));
                        }
                    } else {
                        ps.setString(c + 1, raw);
                    }
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private boolean isNumericColumn(TableModel table, int c) {
        boolean any = false;
        if (table.getRows() == null) {
            return false;
        }
        for (RowModel row : table.getRows()) {
            String raw = cellText(row, c);
            if (raw.isEmpty()) {
                continue;
            }
            if (!NUMBER.matcher(normalizeNumber(raw)).matches()) {
                return false;
            }
            any = true;
        }
        return any;
    }

    private static String normalizeNumber(String raw) {
        return raw.replace("\u00A0", "").replace(" ", "").replace(',', '.');
    }

    private static String cellText(RowModel row, int c) {
        if (row.getCells() == null || c >= row.getCells().size() || row.getCells().get(c) == null) {
            return "";
        }
        Object v = row.getCells().get(c).getRawValue();
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String firstLine(String msg) {
        if (msg == null) {
            return "SQL error";
        }
        int nl = msg.indexOf('\n');
        return nl > 0 ? msg.substring(0, nl) : msg;
    }

    private ColumnType mapJdbcType(int jdbcType) {
        switch (jdbcType) {
            case Types.INTEGER: case Types.BIGINT: case Types.SMALLINT: case Types.TINYINT:
            case Types.FLOAT: case Types.DOUBLE: case Types.DECIMAL: case Types.NUMERIC: case Types.REAL:
                return ColumnType.NUMBER;
            case Types.DATE: case Types.TIME: case Types.TIMESTAMP: case Types.TIMESTAMP_WITH_TIMEZONE:
                return ColumnType.DATE;
            case Types.BOOLEAN: case Types.BIT:
                return ColumnType.BOOLEAN;
            default:
                return ColumnType.TEXT;
        }
    }
}
