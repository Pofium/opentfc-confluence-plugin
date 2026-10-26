package org.opentfc.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.opentfc.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Robust implementation of TableExtractor parsing HTML tables using Jsoup.
 */
public class TableExtractorImpl implements TableExtractor {
    private static final Logger log = LoggerFactory.getLogger(TableExtractorImpl.class);

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[-+]?[0-9]*[.,]?[0-9]+([eE][-+]?[0-9]+)?(%|\\$|€|₽|руб\\.?)?$");
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd",
            "dd.MM.yyyy",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "yyyy-MM-dd HH:mm:ss"
    };

    @Override
    public TableModel extractTable(String htmlContent, String tableId) {
        TableModel model = new TableModel(tableId != null ? tableId : UUID.randomUUID().toString());
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return model;
        }

        try {
            Document doc = Jsoup.parseBodyFragment(htmlContent);
            Element table = doc.selectFirst("table");
            if (table == null) {
                return model;
            }

            // 1. Extract header columns
            Elements headerCells = table.select("thead tr th, thead tr td");
            if (headerCells.isEmpty()) {
                // Check first row of tbody
                Element firstRow = table.selectFirst("tbody tr, tr");
                if (firstRow != null) {
                    headerCells = firstRow.select("th, td");
                }
            }

            int colIdx = 0;
            for (Element th : headerCells) {
                String colName = th.text().trim();
                if (colName.isEmpty()) {
                    colName = "Column " + (colIdx + 1);
                }
                model.addColumn(new ColumnModel(colIdx++, colName, ColumnType.TEXT));
            }

            // 2. Extract rows
            Elements rows = table.select("tbody tr");
            if (rows.isEmpty()) {
                rows = table.select("tr");
                // If header was the first row, skip it
                if (!rows.isEmpty() && table.select("thead").isEmpty()) {
                    rows.remove(0);
                }
            }

            for (Element tr : rows) {
                Elements cells = tr.select("td, th");
                if (cells.isEmpty()) continue;

                RowModel row = new RowModel();
                for (int i = 0; i < model.getColumns().size(); i++) {
                    if (i < cells.size()) {
                        Element td = cells.get(i);
                        String text = td.text().trim();
                        String html = td.html().trim();
                        row.addCell(new CellModel(text, html.isEmpty() ? text : html, ColumnType.TEXT));
                    } else {
                        row.addCell(new CellModel("", "", ColumnType.TEXT));
                    }
                }
                model.addRow(row);
            }

            // 3. Infer column types and unique values
            inferColumnTypes(model);

        } catch (Exception e) {
            log.error("Failed to parse HTML table for tableId={}: {}", tableId, e.getMessage(), e);
        }

        return model;
    }

    private void inferColumnTypes(TableModel model) {
        List<ColumnModel> columns = model.getColumns();
        List<RowModel> rows = model.getRows();

        for (int c = 0; c < columns.size(); c++) {
            ColumnModel col = columns.get(c);
            int numberCount = 0;
            int dateCount = 0;
            int totalNonEmpty = 0;
            Set<String> uniqueValues = new LinkedHashSet<>();

            for (RowModel row : rows) {
                if (c < row.getCells().size()) {
                    CellModel cell = row.getCells().get(c);
                    String val = cell.getRawValue() != null ? cell.getRawValue().toString().trim() : "";
                    if (!val.isEmpty()) {
                        totalNonEmpty++;
                        uniqueValues.add(val);

                        if (isNumeric(val)) {
                            numberCount++;
                        } else if (isDate(val)) {
                            dateCount++;
                        }
                    }
                }
            }

            if (totalNonEmpty > 0) {
                if ((double) numberCount / totalNonEmpty >= 0.8) {
                    col.setType(ColumnType.NUMBER);
                } else if ((double) dateCount / totalNonEmpty >= 0.8) {
                    col.setType(ColumnType.DATE);
                } else if (uniqueValues.size() > 0 && uniqueValues.size() <= 20 && totalNonEmpty > 5) {
                    col.setType(ColumnType.SELECT);
                    col.setOptions(new ArrayList<>(uniqueValues));
                } else {
                    col.setType(ColumnType.TEXT);
                }
            }
        }
    }

    private boolean isNumeric(String val) {
        String cleaned = val.replace(" ", "").replace(",", ".");
        return NUMERIC_PATTERN.matcher(cleaned).matches();
    }

    private boolean isDate(String val) {
        if (val.length() < 8 || val.length() > 25) return false;
        for (String pattern : DATE_PATTERNS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                sdf.setLenient(false);
                sdf.parse(val);
                return true;
            } catch (ParseException ignored) {}
        }
        return false;
    }
}
