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
}
