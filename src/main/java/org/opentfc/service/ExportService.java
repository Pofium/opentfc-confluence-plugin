package org.opentfc.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.opentfc.model.CellModel;
import org.opentfc.model.ColumnModel;
import org.opentfc.model.ColumnType;
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

    /** Excel hard limits: exceeding them must fail fast instead of corrupting the workbook. */
    private static final int MAX_XLSX_COLUMNS = 16_384;
    private static final int MAX_XLSX_ROWS = 1_048_575;
    private static final int MAX_COLUMN_CHARS = 60;

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
     *
     * Column widths are computed from the text length instead of Sheet.autoSizeColumn():
     * autoSizeColumn measures strings through java.awt font metrics, which throws on
     * headless application servers without installed fonts (a documented POI behaviour)
     * and is slow enough on wide tables to time out the request.
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

            List<ColumnModel> cols = table != null ? table.getColumns() : List.of();
            List<RowModel> rows = table != null ? table.getRows() : List.of();
            if (cols.size() > MAX_XLSX_COLUMNS) {
                throw new IOException("Too many columns for XLSX (" + cols.size() + ", max " + MAX_XLSX_COLUMNS + ")");
            }

            // Header Row
            Row headerRow = sheet.createRow(0);
            int[] widths = new int[cols.size()];
            for (int c = 0; c < cols.size(); c++) {
                String name = cols.get(c).getName() != null ? cols.get(c).getName() : "";
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(name);
                cell.setCellStyle(headerStyle);
                widths[c] = name.length();
            }

            // Data Rows
            int limit = Math.min(rows.size(), MAX_XLSX_ROWS);
            for (int r = 0; r < limit; r++) {
                Row dataRow = sheet.createRow(r + 1);
                RowModel rowModel = rows.get(r);
                List<CellModel> cells = rowModel.getCells();
                for (int c = 0; c < cols.size(); c++) {
                    Cell cell = dataRow.createCell(c);
                    ColumnType colType = cols.get(c).getType();
                    CellModel cellModel = cells != null && c < cells.size() ? cells.get(c) : null;
                    widths[c] = Math.max(widths[c], writeCell(cell, cellModel, colType));
                }
            }

            for (int c = 0; c < cols.size(); c++) {
                int chars = Math.min(MAX_COLUMN_CHARS, widths[c] + 2);
                sheet.setColumnWidth(c, chars * 256);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Writes one cell, keeping numbers numeric so Excel can aggregate them.
     *
     * @return rendered text length, used for the column width estimate
     */
    private int writeCell(Cell cell, CellModel cellModel, ColumnType colType) {
        if (cellModel == null) {
            cell.setCellValue("");
            return 0;
        }
        Object val = cellModel.getRawValue();
        if (val instanceof Number) {
            cell.setCellValue(((Number) val).doubleValue());
            return String.valueOf(val).length();
        }
        if (val == null) {
            String html = cellModel.getHtml();
            cell.setCellValue(html != null ? html : "");
            return html != null ? html.length() : 0;
        }
        String text = val.toString();
        // Source tables (CSV/JSON/attachment) carry numbers as strings; converting them here
        // keeps the exported workbook usable for formulas instead of text-formatted digits.
        if (colType == ColumnType.NUMBER) {
            Double number = parseLooseNumber(text);
            if (number != null) {
                cell.setCellValue(number);
                return text.length();
            }
        }
        cell.setCellValue(text);
        return text.length();
    }

    /**
     * Parses a displayed number: "1 234,56", "1.234,56 ₽", "-12,5", "(500)", "42%".
     * Returns null when the text is not a number.
     *
     * Cells arrive as the text the table rendered, so a plain Double.parseDouble() failed on every
     * localized value and Excel received numbers as strings (left-aligned, not summable). The
     * grouping/decimal ambiguity is resolved for ru-RU, the format this plugin renders, and mirrors
     * parseLooseNumber() in the frontend.
     */
    private static Double parseLooseNumber(String text) {
        if (text == null) {
            return null;
        }
        String value = text.replaceAll("[\\s\u00a0\u202f]", "");
        if (value.isEmpty()) {
            return null;
        }

        boolean negative = false;
        if (value.startsWith("(") && value.endsWith(")")) {
            negative = true;
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("-")) {
            negative = true;
            value = value.substring(1);
        } else if (value.startsWith("+")) {
            value = value.substring(1);
        }

        // Only the units this plugin renders may surround the digits. Stripping every non-digit
        // instead (as the first version did) made "Фамилия1", "1С" or "ОКПД 2" parse as numbers,
        // so text columns arrived typed as NUMBER and were exported as digits. Mirrors the
        // parseLooseNumber() of the frontend.
        value = value.replaceAll("(?i)(%|\u2030|\u20bd|[$\u20ac\u00a3\u00a5]|руб\\.?|коп\\.?|грн\\.?)", "");
        if (value.startsWith("-")) {
            negative = true;
            value = value.substring(1);
        } else if (value.startsWith("+")) {
            value = value.substring(1);
        }
        if (!value.matches("[0-9][0-9.,]*")) {
            return null;
        }

        long commas = value.chars().filter(ch -> ch == ',').count();
        long dots = value.chars().filter(ch -> ch == '.').count();
        if (commas > 0 && dots > 0) {
            // Both separators: the rightmost one is the decimal separator
            if (value.lastIndexOf(',') > value.lastIndexOf('.')) {
                value = value.replace(".", "").replace(',', '.');
            } else {
                value = value.replace(",", "");
            }
        } else if (commas > 1 || dots > 1) {
            // Several separators of one kind can only be digit grouping
            value = value.replace(",", "").replace(".", "");
        } else if (commas == 1) {
            value = value.replace(',', '.');
        } else if (dots == 1 && value.matches("\\d{1,3}\\.\\d{3}")) {
            // "1.234" is ru-RU for 1234
            value = value.replace(".", "");
        }

        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return null;
            }
            return negative ? -parsed : parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
