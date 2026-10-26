package org.opentfc.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Covers the table value objects: accessors, collection mutators and the JSON
 * contract used between the backend and the browser bundle.
 */
public class ModelTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testTableModelTracksTotalRows() {
        TableModel table = new TableModel("t1");
        assertEquals("t1", table.getId());
        assertEquals(0, table.getTotalRows());

        table.addColumn(new ColumnModel(0, "A", ColumnType.TEXT));
        RowModel row = new RowModel();
        row.addCell(new CellModel("x", "x", ColumnType.TEXT));
        table.addRow(row);
        assertEquals(1, table.getTotalRows());
        assertEquals(1, table.getColumns().size());

        table.setRows(null);
        assertEquals(0, table.getTotalRows());
    }

    @Test
    public void testTableModelSetters() {
        TableModel table = new TableModel();
        table.setId("id");
        List<ColumnModel> cols = new ArrayList<>();
        cols.add(new ColumnModel(0, "B", ColumnType.NUMBER));
        table.setColumns(cols);
        List<RowModel> rows = new ArrayList<>();
        rows.add(new RowModel());
        table.setRows(rows);
        table.setTotalRows(7);

        assertEquals("id", table.getId());
        assertEquals("B", table.getColumns().get(0).getName());
        assertEquals(1, table.getRows().size());
        assertEquals(7, table.getTotalRows());
    }

    @Test
    public void testColumnModelAccessors() {
        ColumnModel col = new ColumnModel();
        assertNotNull(col.getOptions());
        assertEquals(ColumnType.TEXT, col.getType());

        col.setIndex(3);
        col.setName("Name");
        col.setType(ColumnType.SELECT);
        col.setOptions(List.of("a", "b"));

        assertEquals(3, col.getIndex());
        assertEquals("Name", col.getName());
        assertEquals(ColumnType.SELECT, col.getType());
        assertEquals(2, col.getOptions().size());
    }

    @Test
    public void testRowAndCellModels() {
        CellModel cell = new CellModel();
        assertEquals(ColumnType.TEXT, cell.getType());
        cell.setRawValue(42);
        cell.setHtml("<b>42</b>");
        cell.setType(ColumnType.NUMBER);
        assertEquals(42, cell.getRawValue());
        assertEquals("<b>42</b>", cell.getHtml());
        assertEquals(ColumnType.NUMBER, cell.getType());

        RowModel row = new RowModel();
        row.addCell(cell);
        assertEquals(1, row.getCells().size());

        List<CellModel> cells = new ArrayList<>();
        cells.add(new CellModel("v", "v", ColumnType.TEXT));
        RowModel second = new RowModel(cells);
        assertEquals(1, second.getCells().size());
        second.setCells(new ArrayList<>());
        assertTrue(second.getCells().isEmpty());
    }

    @Test
    public void testSqlExecutionResultFactories() {
        TableModel table = new TableModel("r");
        SqlExecutionResult ok = SqlExecutionResult.success(table, 12L);
        assertTrue(ok.isSuccess());
        assertSame(table, ok.getTable());
        assertEquals(12L, ok.getExecutionTimeMs());
        assertNull(ok.getErrorMessage());

        SqlExecutionResult bad = SqlExecutionResult.error("boom", 3L);
        assertFalse(bad.isSuccess());
        assertEquals("boom", bad.getErrorMessage());
        assertEquals(3L, bad.getExecutionTimeMs());
        assertNull(bad.getTable());

        SqlExecutionResult empty = new SqlExecutionResult();
        empty.setSuccess(true);
        empty.setTable(table);
        empty.setExecutionTimeMs(1L);
        empty.setErrorMessage("e");
        assertTrue(empty.isSuccess());
        assertSame(table, empty.getTable());
        assertEquals(1L, empty.getExecutionTimeMs());
        assertEquals("e", empty.getErrorMessage());
    }

    @Test
    public void testFilterProfileModelAccessors() {
        FilterProfileModel p = new FilterProfileModel(1L, 10L, "macro", "My profile", "{}");
        assertEquals(1L, p.getId());
        assertEquals(10L, p.getPageId());
        assertEquals("macro", p.getMacroId());
        assertEquals("My profile", p.getName());
        assertEquals("{}", p.getStateJson());

        p.setId(2L);
        p.setPageId(20L);
        p.setMacroId("m2");
        p.setName("other");
        p.setStateJson("{\"a\":1}");

        assertEquals(2L, p.getId());
        assertEquals(20L, p.getPageId());
        assertEquals("m2", p.getMacroId());
        assertEquals("other", p.getName());
        assertEquals("{\"a\":1}", p.getStateJson());
    }

    @Test
    public void testTableModelJsonContract() throws Exception {
        TableModel table = new TableModel("json-id");
        table.addColumn(new ColumnModel(0, "Col", ColumnType.NUMBER));
        RowModel row = new RowModel();
        row.addCell(new CellModel(5, "5", ColumnType.NUMBER));
        table.addRow(row);

        String json = mapper.writeValueAsString(table);
        assertTrue(json.contains("\"id\":\"json-id\""));
        assertTrue(json.contains("\"columns\""));
        assertTrue(json.contains("\"totalRows\":1"));
        // Cell payload uses the "val" alias understood by the frontend bundle.
        assertTrue(json.contains("\"val\":5"));

        TableModel back = mapper.readValue(json, TableModel.class);
        assertEquals("json-id", back.getId());
        assertEquals(1, back.getColumns().size());
        assertEquals("Col", back.getColumns().get(0).getName());
        assertEquals(1, back.getTotalRows());
    }

    @Test
    public void testUnknownJsonFieldsAreIgnored() throws Exception {
        String json = "{\"id\":\"x\",\"columns\":[],\"rows\":[],\"somethingNew\":true}";
        TableModel table = mapper.readValue(json, TableModel.class);
        assertEquals("x", table.getId());
        assertTrue(table.getColumns().isEmpty());
    }
}
