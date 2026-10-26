package org.opentfc.service;

import org.junit.Before;
import org.junit.Test;
import org.opentfc.model.ColumnType;
import org.opentfc.model.TableModel;

import static org.junit.Assert.*;

public class TableExtractorTest {

    private TableExtractor tableExtractor;

    @Before
    public void setUp() {
        tableExtractor = new TableExtractorImpl();
    }

    @Test
    public void testExtractSimpleHtmlTable() {
        String html = "<table class=\"confluenceTable\">" +
                "<thead><tr><th>Name</th><th>Age</th><th>City</th></tr></thead>" +
                "<tbody>" +
                "<tr><td>Alice</td><td>25</td><td>New York</td></tr>" +
                "<tr><td>Bob</td><td>30</td><td>London</td></tr>" +
                "<tr><td>Charlie</td><td>35</td><td>Paris</td></tr>" +
                "</tbody></table>";

        TableModel model = tableExtractor.extractTable(html, "t-1");

        assertNotNull(model);
        assertEquals("t-1", model.getId());
        assertEquals(3, model.getColumns().size());
        assertEquals("Name", model.getColumns().get(0).getName());
        assertEquals("Age", model.getColumns().get(1).getName());
        assertEquals("City", model.getColumns().get(2).getName());
        assertEquals(3, model.getRows().size());
        assertEquals("Alice", model.getRows().get(0).getCells().get(0).getRawValue());
        assertEquals("25", model.getRows().get(0).getCells().get(1).getRawValue());
        assertEquals(ColumnType.TEXT, model.getColumns().get(0).getType());
        assertEquals(ColumnType.NUMBER, model.getColumns().get(1).getType());
    }

    @Test
    public void testExtractTableWithoutThead() {
        String html = "<table>" +
                "<tr><th>Name</th><th>Value</th></tr>" +
                "<tr><td>A</td><td>1</td></tr>" +
                "</table>";
        TableModel model = tableExtractor.extractTable(html, "t");
        assertEquals(2, model.getColumns().size());
        assertEquals("Name", model.getColumns().get(0).getName());
        assertEquals(1, model.getRows().size());
        assertEquals("A", model.getRows().get(0).getCells().get(0).getRawValue());
    }

    @Test
    public void testInferDateColumn() {
        String html = "<table>" +
                "<tr><th>Date</th><th>Label</th></tr>" +
                "<tr><td>2024-01-01</td><td>x</td></tr>" +
                "<tr><td>2024-01-02</td><td>y</td></tr>" +
                "<tr><td>2024-01-03</td><td>z</td></tr>" +
                "</table>";
        TableModel model = tableExtractor.extractTable(html, "t");
        assertEquals(ColumnType.DATE, model.getColumns().get(0).getType());
        assertEquals(ColumnType.TEXT, model.getColumns().get(1).getType());
    }

    @Test
    public void testInferSelectColumn() {
        StringBuilder html = new StringBuilder("<table><tr><th>Cat</th><th>V</th></tr>");
        String[] cats = {"red", "blue", "green"};
        for (int i = 0; i < 9; i++) {
            html.append("<tr><td>").append(cats[i % 3]).append("</td><td>").append(i).append("</td></tr>");
        }
        html.append("</table>");
        TableModel model = tableExtractor.extractTable(html.toString(), "t");
        assertEquals(ColumnType.SELECT, model.getColumns().get(0).getType());
        assertNotNull(model.getColumns().get(0).getOptions());
        assertEquals(3, model.getColumns().get(0).getOptions().size());
    }

    @Test
    public void testNumericWithCurrencySuffix() {
        String html = "<table><tr><th>Price</th></tr>" +
                "<tr><td>100₽</td></tr>" +
                "<tr><td>200₽</td></tr>" +
                "<tr><td>300₽</td></tr></table>";
        TableModel model = tableExtractor.extractTable(html, "t");
        assertEquals(ColumnType.NUMBER, model.getColumns().get(0).getType());
    }

    @Test
    public void testHtmlEntitiesInCells() {
        String html = "<table><thead><tr><th>A</th></tr></thead>" +
                "<tbody><tr><td>AT&amp;T</td></tr></tbody></table>";
        TableModel model = tableExtractor.extractTable(html, "t");
        assertEquals("AT&T", model.getRows().get(0).getCells().get(0).getRawValue());
    }

    @Test
    public void testMissingCellsArePadded() {
        String html = "<table><tr><th>A</th><th>B</th><th>C</th></tr>" +
                "<tr><td>1</td><td>2</td></tr></table>";
        TableModel model = tableExtractor.extractTable(html, "t");
        assertEquals(1, model.getRows().size());
        assertEquals(3, model.getRows().get(0).getCells().size());
        assertEquals("", model.getRows().get(0).getCells().get(2).getRawValue());
    }

    @Test
    public void testEmptyHtmlTable() {
        TableModel model = tableExtractor.extractTable("", "empty");
        assertNotNull(model);
        assertEquals(0, model.getColumns().size());
        assertEquals(0, model.getRows().size());
    }

    @Test
    public void testNullHtmlTable() {
        TableModel model = tableExtractor.extractTable(null, "null");
        assertNotNull(model);
        assertEquals(0, model.getColumns().size());
    }

    @Test
    public void testHtmlWithoutTableReturnsEmptyModel() {
        TableModel model = tableExtractor.extractTable("<p>no table here</p>", "none");
        assertEquals(0, model.getColumns().size());
    }
}
