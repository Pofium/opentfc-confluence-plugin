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

    @Test
    public void testSelectQuery() {
        TableModel t1 = new TableModel("t1");
        t1.addColumn(new ColumnModel(0, "Name", ColumnType.TEXT));
        t1.addColumn(new ColumnModel(1, "Score", ColumnType.NUMBER));

        RowModel r1 = new RowModel();
        r1.addCell(new CellModel("Bob", "Bob", ColumnType.TEXT));
        r1.addCell(new CellModel(85, "85", ColumnType.NUMBER));
        t1.addRow(r1);

        RowModel r2 = new RowModel();
        r2.addCell(new CellModel("Alice", "Alice", ColumnType.TEXT));
        r2.addCell(new CellModel(95, "95", ColumnType.NUMBER));
        t1.addRow(r2);

        String sql = "SELECT Name, CAST(Score AS INTEGER) as Score FROM T1 ORDER BY Score DESC";
        SqlExecutionResult result = engine.executeQuery(sql, List.of(t1));

        assertTrue(result.isSuccess());
        assertNotNull(result.getTable());
        assertEquals(2, result.getTable().getRows().size());

        // First row should be Alice (95)
        assertEquals("Alice", result.getTable().getRows().get(0).getCells().get(0).getRawValue());
    }

    @Test
    public void testDisallowedQueryRejected() {
        TableModel t1 = new TableModel("t1");
        t1.addColumn(new ColumnModel(0, "Name", ColumnType.TEXT));

        String sql = "DROP TABLE T1";
        SqlExecutionResult result = engine.executeQuery(sql, List.of(t1));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Security violation"));
    }
}
