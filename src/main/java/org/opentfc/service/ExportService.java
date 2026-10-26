package org.opentfc.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.opentfc.model.CellModel;
import org.opentfc.model.ColumnModel;
import org.opentfc.model.RowModel;
import org.opentfc.model.TableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for exporting TableModel to CSV and Excel (XLSX).
 */
public class ExportService {
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    /**
     * Export TableModel to CSV string.
     */
    public String exportToCsv(TableModel table) throws IOException {
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            // Write headers
            List<String> headers = new ArrayList<>();
            for (ColumnModel col : table.getColumns()) {
                headers.add(col.getName());
            }
            printer.printRecord(headers);

            // Write rows
            for (RowModel row : table.getRows()) {
                List<String> values = new ArrayList<>();
                for (CellModel cell : row.getCells()) {
                    values.add(cell.getRawValue() != null ? cell.getRawValue().toString() : "");
                }
                printer.printRecord(values);
            }
        }
        return writer.toString();
    }

    /**
     * Export TableModel to XLSX bytes.
     */
    public byte[] exportToXlsx(TableModel table) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sheet1");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Header Row
            Row headerRow = sheet.createRow(0);
            List<ColumnModel> cols = table.getColumns();
            for (int c = 0; c < cols.size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(cols.get(c).getName());
                cell.setCellStyle(headerStyle);
            }

            // Data Rows
            List<RowModel> rows = table.getRows();
            for (int r = 0; r < rows.size(); r++) {
                Row dataRow = sheet.createRow(r + 1);
                RowModel rowModel = rows.get(r);
                for (int c = 0; c < cols.size(); c++) {
                    Cell cell = dataRow.createCell(c);
                    if (c < rowModel.getCells().size()) {
                        CellModel cellModel = rowModel.getCells().get(c);
                        Object val = cellModel.getRawValue();
                        if (val instanceof Number) {
                            cell.setCellValue(((Number) val).doubleValue());
                        } else if (val != null) {
                            cell.setCellValue(val.toString());
                        } else {
                            cell.setCellValue("");
                        }
                    }
                }
            }

            // Auto-size columns (capped at 50 chars)
            for (int c = 0; c < cols.size(); c++) {
                sheet.autoSizeColumn(c);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
