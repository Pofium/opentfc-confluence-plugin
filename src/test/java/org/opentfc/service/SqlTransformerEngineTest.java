package org.opentfc.service;

import org.junit.Before;
import org.junit.Test;
import org.opentfc.model.*;

import java.util.List;

import static org.junit.Assert.*;

public class SqlTransformerEngineTest {

    private SqlTransformerEngine engine;

    @Before
    public void setUp() {
        engine = new SqlTransformerEngine();
    }

    private TableModel employees() {
        TableModel t = new TableModel("employees");
        t.addColumn(new ColumnModel(0, "Name", ColumnType.TEXT));
        t.addColumn(new ColumnModel(1, "Department", ColumnType.TEXT));
        t.addColumn(new ColumnModel(2, "Salary", ColumnType.NUMBER));
        Object[][] data = {
                {"Bob", "Eng", 85},
                {"Alice", "Eng", 95},
                {"Carol", "Ops", 70},
                {"Dave", "Ops", 60}
        };
        for (Object[] row : data) {
            RowModel r = new RowModel();
            r.addCell(new CellModel(row[0], String.valueOf(row[0]), ColumnType.TEXT));
            r.addCell(new CellModel(row[1], String.valueOf(row[1]), ColumnType.TEXT));
            r.addCell(new CellModel(row[2], String.valueOf(row[2]), ColumnType.NUMBER));
            t.addRow(r);
        }
        return t;
    }

    private TableModel departments() {
        TableModel t = new TableModel("departments");
        t.addColumn(new ColumnModel(0, "Name", ColumnType.TEXT));
        t.addColumn(new ColumnModel(1, "Budget", ColumnType.NUMBER));
        RowModel r1 = new RowModel();
        r1.addCell(new CellModel("Eng", "Eng", ColumnType.TEXT));
        r1.addCell(new CellModel(1000, "1000", ColumnType.NUMBER));
        t.addRow(r1);
        RowModel r2 = new RowModel();
        r2.addCell(new CellModel("Ops", "Ops", ColumnType.TEXT));
        r2.addCell(new CellModel(500, "500", ColumnType.NUMBER));
        t.addRow(r2);
        return t;
    }

    @Test
    public void testSelectQuery() {
        String sql = "SELECT Name, CAST(Salary AS INTEGER) AS Salary FROM T1 ORDER BY Salary DESC";
        SqlExecutionResult result = engine.executeQuery(sql, List.of(employees()));
        assertTrue(result.isSuccess());
        assertNotNull(result.getTable());
        assertEquals(4, result.getTable().getRows().size());
        assertEquals("Alice", result.getTable().getRows().get(0).getCells().get(0).getRawValue());
    }

    @Test
    public void testJoinQuery() {
        String sql = "SELECT e.Name, d.Budget FROM T1 e JOIN T2 d ON e.Department = d.Name ORDER BY e.Name";
        SqlExecutionResult result = engine.executeQuery(sql, List.of(employees(), departments()));
        assertTrue(result.isSuccess());
        assertEquals(4, result.getTable().getRows().size());
        for (RowModel row : result.getTable().getRows()) {
            String budget = String.valueOf(row.getCells().get(1).getRawValue());
            assertTrue(budget.equals("1000") || budget.equals("500"));
        }
    }

    @Test
    public void testGroupByAggregation() {
        String sql = "SELECT Department, SUM(CAST(Salary AS INTEGER)) AS Total FROM T1 GROUP BY Department ORDER BY Department";
        SqlExecutionResult result = engine.executeQuery(sql, List.of(employees()));
        assertTrue(result.isSuccess());
        assertEquals(2, result.getTable().getRows().size());
        RowModel first = result.getTable().getRows().get(0);
        if ("Eng".equals(first.getCells().get(0).getRawValue())) {
            assertEquals("180", String.valueOf(first.getCells().get(1).getRawValue()));
        } else {
            assertEquals("130", String.valueOf(first.getCells().get(1).getRawValue()));
        }
    }

    @Test
    public void testWindowFunction() {
        String sql = "SELECT Name, ROW_NUMBER() OVER (ORDER BY Salary DESC) AS rn FROM T1 ORDER BY rn";
        SqlExecutionResult result = engine.executeQuery(sql, List.of(employees()));
        assertTrue(result.isSuccess());
        assertEquals(4, result.getTable().getRows().size());
        assertEquals("Alice", result.getTable().getRows().get(0).getCells().get(0).getRawValue());
        assertEquals("Dave", result.getTable().getRows().get(3).getCells().get(0).getRawValue());
    }

    @Test
    public void testUnionQuery() {
        TableModel a = new TableModel("a");
        a.addColumn(new ColumnModel(0, "V", ColumnType.TEXT));
        RowModel r1 = new RowModel();
        r1.addCell(new CellModel("x", "x", ColumnType.TEXT));
        a.addRow(r1);
        TableModel b = new TableModel("b");
        b.addColumn(new ColumnModel(0, "V", ColumnType.TEXT));
        RowModel r2 = new RowModel();
        r2.addCell(new CellModel("y", "y", ColumnType.TEXT));
        b.addRow(r2);

        String sql = "SELECT V FROM T1 UNION ALL SELECT V FROM T2 ORDER BY V";
        SqlExecutionResult result = engine.executeQuery(sql, List.of(a, b));
        assertTrue(result.isSuccess());
        assertEquals(2, result.getTable().getRows().size());
    }

    @Test
    public void testColumnNamesWithSpacesAndQuotes() {
        TableModel t = new TableModel("spaced");
        t.addColumn(new ColumnModel(0, "Column Name", ColumnType.TEXT));
        RowModel r = new RowModel();
        r.addCell(new CellModel("value1", "value1", ColumnType.TEXT));
        t.addRow(r);

        String sql = "SELECT T1.\"Column Name\" FROM T1";
        SqlExecutionResult result = engine.executeQuery(sql, List.of(t));
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTable().getRows().size());
        assertEquals("value1", result.getTable().getRows().get(0).getCells().get(0).getRawValue());
    }

    @Test
    public void testDisallowedQueryRejected() {
        SqlExecutionResult result = engine.executeQuery("DROP TABLE T1", List.of(employees()));
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Security violation"));
    }

    @Test
    public void testDmlQueryRejected() {
        SqlExecutionResult result = engine.executeQuery("UPDATE T1 SET Name='x'", List.of(employees()));
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Security violation"));
    }

    @Test
    public void testMultiStatementWithDdlRejected() {
        SqlExecutionResult result = engine.executeQuery("SELECT * FROM T1; DROP TABLE T1", List.of(employees()));
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Security violation"));
    }

    @Test
    public void testStringLiteralContainingKeywordIsAllowed() {
        SqlExecutionResult result = engine.executeQuery("SELECT 'DROP' AS msg FROM T1", List.of(employees()));
        assertTrue(result.isSuccess());
        assertEquals("DROP", result.getTable().getRows().get(0).getCells().get(0).getRawValue());
    }

    @Test
    public void testLineCommentContainingKeywordIsAllowed() {
        SqlExecutionResult result = engine.executeQuery("SELECT Name FROM T1 -- harmless DROP mention", List.of(employees()));
        assertTrue(result.isSuccess());
    }

    @Test
    public void testBlockCommentContainingKeywordIsAllowed() {
        SqlExecutionResult result = engine.executeQuery("SELECT /* DROP TABLE evil */ Name FROM T1", List.of(employees()));
        assertTrue(result.isSuccess());
    }

    @Test
    public void testQuotedIdentifierContainingKeywordIsAllowed() {
        TableModel t = new TableModel("t");
        t.addColumn(new ColumnModel(0, "select", ColumnType.TEXT));
        RowModel r = new RowModel();
        r.addCell(new CellModel("x", "x", ColumnType.TEXT));
        t.addRow(r);
        SqlExecutionResult result = engine.executeQuery("SELECT T1.\"select\" FROM T1", List.of(t));
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTable().getRows().size());
    }

    @Test
    public void testEmptyQueryRejected() {
        SqlExecutionResult result = engine.executeQuery("", List.of(employees()));
        assertFalse(result.isSuccess());
        assertFalse(result.getErrorMessage().isEmpty());
    }

    @Test
    public void testNullQueryRejected() {
        SqlExecutionResult result = engine.executeQuery(null, List.of(employees()));
        assertFalse(result.isSuccess());
    }

    @Test
    public void testSqlSyntaxErrorReturnsErrorWithoutThrowing() {
        SqlExecutionResult result = engine.executeQuery("SELECT * FROM", List.of(employees()));
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testStripSqlNoiseUnit() {
        assertFalse(SqlTransformerEngine.stripSqlNoise("SELECT 'DROP'").matches("(?s).*DROP.*"));
        assertFalse(SqlTransformerEngine.stripSqlNoise("SELECT \"CREATE\"").matches("(?s).*CREATE.*"));
        assertFalse(SqlTransformerEngine.stripSqlNoise("SELECT 1 -- DROP").matches("(?s).*DROP.*"));
        assertFalse(SqlTransformerEngine.stripSqlNoise("SELECT 1 /* DROP */").matches("(?s).*DROP.*"));
        assertTrue(SqlTransformerEngine.stripSqlNoise("SELECT 1; DROP TABLE t").matches("(?s).*DROP.*"));
    }
}
