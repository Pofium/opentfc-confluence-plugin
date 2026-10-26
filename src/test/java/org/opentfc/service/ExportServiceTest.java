package org.opentfc.service;

import org.junit.Test;
import org.opentfc.model.*;

import java.io.IOException;

import static org.junit.Assert.*;

public class ExportServiceTest {

    @Test
    public void testExportCsvAndXlsx() throws IOException {
        ExportService exportService = new ExportService();

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

        // Test CSV
        String csv = exportService.exportToCsv(table);
        assertNotNull(csv);
        assertTrue(csv.contains("Item,Price"));
        assertTrue(csv.contains("Laptop,1200.5"));
        assertTrue(csv.contains("Mouse,25.0"));

        // Test XLSX
        byte[] xlsxBytes = exportService.exportToXlsx(table);
        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 0);
    }
}
