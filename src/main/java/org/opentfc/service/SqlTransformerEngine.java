package org.opentfc.service;

import org.opentfc.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Sandboxed In-Memory OLAP SQL Engine using DuckDB JDBC.
 */
public class SqlTransformerEngine {
    private static final Logger log = LoggerFactory.getLogger(SqlTransformerEngine.class);

    private static final Pattern DISALLOWED_SQL = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|EXEC|EXECUTE|GRANT|REVOKE|COPY|PRAGMA|ATTACH|DETACH|INSTALL|LOAD)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int MAX_RESULT_ROWS = 50000;

    /**
     * Executes SQL over provided input tables (mapped as T1, T2, ...).
     *
     * @param sql SELECT query from user
     * @param sourceTables Ordered list of source tables
     * @return Execution result with TableModel or error message
     */
    public SqlExecutionResult executeQuery(String sql, List<TableModel> sourceTables) {
        long startTime = System.currentTimeMillis();

        if (sql == null || sql.trim().isEmpty()) {
            return SqlExecutionResult.error("SQL query is empty", 0);
        }

        String trimmedSql = sql.trim();
        if (DISALLOWED_SQL.matcher(trimmedSql).find()) {
            return SqlExecutionResult.error("Security violation: Only SELECT queries are permitted", 0);
        }

        // Open in-memory DuckDB connection
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            // Set safety limits
            try (Statement cfgStmt = conn.createStatement()) {
                cfgStmt.execute("SET preserve_insertion_order=false;");
            }

            // 1. Create and populate tables T1, T2, ...
            for (int i = 0; i < sourceTables.size(); i++) {
                String tableName = "T" + (i + 1);
                TableModel table = sourceTables.get(i);
                createAndPopulateTable(conn, tableName, table);
            }

            // 2. Execute query
            try (PreparedStatement stmt = conn.prepareStatement(trimmedSql)) {
                stmt.setQueryTimeout(DEFAULT_TIMEOUT_SECONDS);

                try (ResultSet rs = stmt.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    TableModel resultTable = new TableModel(UUID.randomUUID().toString());
                    for (int c = 1; c <= colCount; c++) {
                        String colName = meta.getColumnLabel(c);
                        int colType = meta.getColumnType(c);
                        ColumnType type = mapJdbcType(colType);
                        resultTable.addColumn(new ColumnModel(c - 1, colName, type));
                    }

                    int rowCount = 0;
                    while (rs.next() && rowCount < MAX_RESULT_ROWS) {
                        RowModel row = new RowModel();
                        for (int c = 1; c <= colCount; c++) {
                            Object val = rs.getObject(c);
                            String strVal = val != null ? String.valueOf(val) : "";
                            row.addCell(new CellModel(val, strVal, resultTable.getColumns().get(c - 1).getType()));
                        }
                        resultTable.addRow(row);
                        rowCount++;
                    }

                    long duration = System.currentTimeMillis() - startTime;
                    return SqlExecutionResult.success(resultTable, duration);
                }
            }
        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("SQL transformer execution error: {}", e.getMessage());
            return SqlExecutionResult.error("SQL Error: " + e.getMessage(), duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Unexpected error during SQL transformation: {}", e.getMessage(), e);
            return SqlExecutionResult.error("Execution failed: " + e.getMessage(), duration);
        }
    }

    private void createAndPopulateTable(Connection conn, String tableName, TableModel table) throws SQLException {
        List<ColumnModel> cols = table.getColumns();
        if (cols.isEmpty()) {
            return;
        }

        StringBuilder createSql = new StringBuilder("CREATE TEMPORARY TABLE ").append(tableName).append(" (");
        for (int c = 0; c < cols.size(); c++) {
            if (c > 0) createSql.append(", ");
            String colName = cols.get(c).getName().replace("\"", "\"\"");
            createSql.append("\"").append(colName).append("\" VARCHAR");
        }
        createSql.append(");");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSql.toString());
        }

        // Insert rows
        if (!table.getRows().isEmpty()) {
            StringBuilder insertSql = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES (");
            for (int c = 0; c < cols.size(); c++) {
                if (c > 0) insertSql.append(", ");
                insertSql.append("?");
            }
            insertSql.append(")");

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql.toString())) {
                for (RowModel row : table.getRows()) {
                    for (int c = 0; c < cols.size(); c++) {
                        String val = "";
                        if (c < row.getCells().size() && row.getCells().get(c).getRawValue() != null) {
                            val = String.valueOf(row.getCells().get(c).getRawValue());
                        }
                        insertStmt.setString(c + 1, val);
                    }
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
        }
    }

    private ColumnType mapJdbcType(int jdbcType) {
        switch (jdbcType) {
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.FLOAT:
            case Types.DOUBLE:
            case Types.DECIMAL:
            case Types.NUMERIC:
                return ColumnType.NUMBER;
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                return ColumnType.DATE;
            case Types.BOOLEAN:
            case Types.BIT:
                return ColumnType.BOOLEAN;
            default:
                return ColumnType.TEXT;
        }
    }
}
