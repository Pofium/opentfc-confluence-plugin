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

        // Check inferred column types
        assertEquals(ColumnType.TEXT, model.getColumns().get(0).getType());
        assertEquals(ColumnType.NUMBER, model.getColumns().get(1).getType());
    }

    @Test
    public void testEmptyHtmlTable() {
        TableModel model = tableExtractor.extractTable("", "empty");
        assertNotNull(model);
        assertEquals(0, model.getColumns().size());
        assertEquals(0, model.getRows().size());
    }
}
