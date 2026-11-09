package org.opentfc.service;

import org.junit.Test;
import org.opentfc.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.*;

public class ExportServiceTest {

    private TableModel sampleTable() {
        TableModel table = new TableModel("test-table");
        table.addColumn(new ColumnModel(0, "Item", ColumnType.TEXT));
        table.addColumn(new ColumnModel(1, "Price", ColumnType.NUMBER));

        RowModel row1 = new RowModel();
        row1.addCell(new CellModel("Laptop", "Laptop", ColumnType.TEXT));
        row1.addCell(new CellModel(1200.5, "1200.5", ColumnType.NUMBER));
        table.addRow(row1);

        RowModel row2 = new RowModel();
        row2.addCell(new CellModel("Mouse", "Mouse", ColumnType.TEXT));
        row2.addCell(new CellModel(25.0, "25.0", ColumnType.NUMBER));
        table.addRow(row2);
        return table;
    }

    @Test
    public void testExportCsv() throws IOException {
        String csv = new ExportService().exportToCsv(sampleTable());
        assertNotNull(csv);
        assertTrue(csv.contains("Item,Price"));
        assertTrue(csv.contains("Laptop,1200.5"));
        assertTrue(csv.contains("Mouse,25.0"));
    }

    @Test
    public void testExportCsvEmptyTable() throws IOException {
        TableModel empty = new TableModel("empty");
        empty.addColumn(new ColumnModel(0, "A", ColumnType.TEXT));
        String csv = new ExportService().exportToCsv(empty);
        assertTrue(csv.contains("A"));
        assertFalse(csv.contains("B"));
    }

    @Test
    public void testExportCsvQuotesSpecialCharacters() throws IOException {
        TableModel t = new TableModel("t");
        t.addColumn(new ColumnModel(0, "A", ColumnType.TEXT));
        RowModel r = new RowModel();
        r.addCell(new CellModel("comma,inside", "comma,inside", ColumnType.TEXT));
        t.addRow(r);
        String csv = new ExportService().exportToCsv(t);
        assertTrue(csv.contains("\"comma,inside\""));
    }

    @Test
    public void testExportXlsxRoundTrip() throws IOException {
        byte[] xlsxBytes = new ExportService().exportToXlsx(sampleTable());
        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Item", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Price", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("Laptop", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(1200.5, sheet.getRow(1).getCell(1).getNumericCellValue(), 0.0001);
            assertEquals("Mouse", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(25.0, sheet.getRow(2).getCell(1).getNumericCellValue(), 0.0001);
        }
    }

    @Test
    public void testExportXlsxEmptyTable() throws IOException {
        TableModel empty = new TableModel("empty");
        empty.addColumn(new ColumnModel(0, "A", ColumnType.TEXT));
        byte[] bytes = new ExportService().exportToXlsx(empty);
        assertTrue(bytes.length > 0);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals("A", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
    }

    /**
     * Exported workbooks must stay aggregatable: the frontend sends the rendered cell text, so
     * "1 234,56 ₽" has to become a number rather than a left-aligned string.
     */
    @Test
    public void testExportXlsxConvertsFormattedNumbers() throws IOException {
        TableModel t = new TableModel("t");
        t.addColumn(new ColumnModel(0, "Price", ColumnType.NUMBER));
        addTextRow(t, "1 234,56 \u20bd");
        addTextRow(t, "12,5");
        addTextRow(t, "1.234,56");
        addTextRow(t, "(500)");
        addTextRow(t, "42%");
        addTextRow(t, "not a number");
        // Labels that merely contain digits must not be turned into numbers: the frontend types
        // columns by parsing the displayed text, and stripping every non-digit used to make
        // "\u0424\u0430\u043c\u0438\u043b\u0438\u044f1" look like 1.
        addTextRow(t, "\u0424\u0430\u043c\u0438\u043b\u0438\u044f1");
        addTextRow(t, "1\u0421:\u041f\u0440\u0435\u0434\u043f\u0440\u0438\u044f\u0442\u0438\u0435");
        addTextRow(t, "12,5 \u0448\u0442");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(new ExportService().exportToXlsx(t)))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(1234.56, sheet.getRow(1).getCell(0).getNumericCellValue(), 0.0001);
            assertEquals(12.5, sheet.getRow(2).getCell(0).getNumericCellValue(), 0.0001);
            assertEquals(1234.56, sheet.getRow(3).getCell(0).getNumericCellValue(), 0.0001);
            assertEquals(-500.0, sheet.getRow(4).getCell(0).getNumericCellValue(), 0.0001);
            assertEquals(42.0, sheet.getRow(5).getCell(0).getNumericCellValue(), 0.0001);
            assertEquals("not a number", sheet.getRow(6).getCell(0).getStringCellValue());
            assertEquals("\u0424\u0430\u043c\u0438\u043b\u0438\u044f1", sheet.getRow(7).getCell(0).getStringCellValue());
            assertEquals("1\u0421:\u041f\u0440\u0435\u0434\u043f\u0440\u0438\u044f\u0442\u0438\u0435", sheet.getRow(8).getCell(0).getStringCellValue());
            assertEquals("12,5 \u0448\u0442", sheet.getRow(9).getCell(0).getStringCellValue());
        }
    }

    @Test
    public void testExportXlsxKeepsTextColumnsAsText() throws IOException {
        TableModel t = new TableModel("t");
        t.addColumn(new ColumnModel(0, "Sku", ColumnType.TEXT));
        addTextRow(t, "007");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(new ExportService().exportToXlsx(t)))) {
            Cell cell = workbook.getSheetAt(0).getRow(1).getCell(0);
            assertEquals(CellType.STRING, cell.getCellType());
            assertEquals("007", cell.getStringCellValue());
        }
    }

    /**
     * Widths come from the text length, never from Sheet.autoSizeColumn(), whose AWT font metrics
     * throw on headless application servers.
     */
    @Test
    public void testExportXlsxComputesColumnWidthsWithoutAwt() throws IOException {
        TableModel t = new TableModel("t");
        t.addColumn(new ColumnModel(0, "Short", ColumnType.TEXT));
        t.addColumn(new ColumnModel(1, "A very long column header with lots of text inside", ColumnType.TEXT));
        RowModel row = new RowModel();
        row.addCell(new CellModel("a", "a", ColumnType.TEXT));
        row.addCell(new CellModel("bbbbbbbbbb", "bbbbbbbbbb", ColumnType.TEXT));
        t.addRow(row);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(new ExportService().exportToXlsx(t)))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertTrue(sheet.getColumnWidth(0) > 0);
            assertTrue(sheet.getColumnWidth(1) > sheet.getColumnWidth(0));
            // capped at 60 characters (256 units each)
            assertTrue(sheet.getColumnWidth(1) <= 60 * 256);
        }
    }

    @Test
    public void testExportXlsxToleratesRowsWithMissingCells() throws IOException {
        TableModel t = new TableModel("t");
        t.addColumn(new ColumnModel(0, "A", ColumnType.TEXT));
        t.addColumn(new ColumnModel(1, "B", ColumnType.NUMBER));
        RowModel shortRow = new RowModel();
        shortRow.addCell(new CellModel("only one cell", "only one cell", ColumnType.TEXT));
        t.addRow(shortRow);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(new ExportService().exportToXlsx(t)))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("only one cell", sheet.getRow(1).getCell(0).getStringCellValue());
            Cell absent = sheet.getRow(1).getCell(1);
            assertTrue(absent == null || absent.getCellType() == CellType.BLANK
                    || absent.getCellType() == CellType.STRING && absent.getStringCellValue().isEmpty());
        }
    }

    private static void addTextRow(TableModel t, String value) {
        RowModel row = new RowModel();
        row.addCell(new CellModel(value, value, ColumnType.NUMBER));
        t.addRow(row);
    }
}
