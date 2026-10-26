package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;
import org.opentfc.model.TableModel;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Verifies macro render behavior: parameter mapping into the data-opentfc-config
 * JSON, HTML escaping (XSS), the static export fallback (PDF/Word/email) and the
 * attachment-table listing.
 */
public class MacroRenderTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConversionContext contextWithOutputType(String outputType) {
        ConversionContext ctx = mock(ConversionContext.class);
        when(ctx.getOutputType()).thenReturn(outputType);
        return ctx;
    }

    private Element parseRoot(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        Element root = doc.selectFirst(".opentfc-container");
        assertNotNull("Expected .opentfc-container in output", root);
        return root;
    }

    private Map<String, Object> readConfig(Element root) throws Exception {
        String json = root.attr("data-opentfc-config");
        assertFalse("data-opentfc-config attribute missing or empty", json.isEmpty());
        return MAPPER.readValue(json, Map.class);
    }

    @Test
    public void testTableFilterDefaultRender() throws Exception {
        TableFilterMacro macro = new TableFilterMacro();
        String out = macro.execute(new HashMap<>(), "<table><tr><td>a</td></tr></table>",
                contextWithOutputType(null));

        Element root = parseRoot(out);
        assertTrue("macroId should be auto-generated when absent", root.id().startsWith("otfc-"));
        Map<String, Object> cfg = readConfig(root);
        assertEquals("25", cfg.get("pageSize"));
        assertEquals(Boolean.TRUE, cfg.get("freezeHeader"));
        assertEquals(0, ((Number) cfg.get("freezeColumns")).intValue());
        assertEquals(Boolean.TRUE, cfg.get("filterStatus"));
        assertEquals(Boolean.TRUE, cfg.get("enablePagination"));
    }

    @Test
    public void testTableFilterUsesProvidedMacroId() throws Exception {
        TableFilterMacro macro = new TableFilterMacro();
        Map<String, String> params = new HashMap<>();
        params.put("macroId", "my-filter-id");
        params.put("pageSize", "50");
        params.put("freezeColumns", "2");
        params.put("totalsRow", "true");

        String out = macro.execute(params, "<table></table>", contextWithOutputType(null));
        Element root = parseRoot(out);
        assertEquals("my-filter-id", root.id());
        Map<String, Object> cfg = readConfig(root);
        assertEquals("50", cfg.get("pageSize"));
        assertEquals(2, ((Number) cfg.get("freezeColumns")).intValue());
        assertEquals(Boolean.TRUE, cfg.get("totalsRow"));
    }

    @Test
    public void testTableFilterExportModeReturnsStaticBody() throws Exception {
        TableFilterMacro macro = new TableFilterMacro();
        String body = "<table><tr><td>static data</td></tr></table>";
        String out = macro.execute(new HashMap<>(), body, contextWithOutputType("pdf"));

        assertTrue(out.contains("opentfc-export-table"));
        assertTrue(out.contains("static data"));
        assertFalse("Interactive container must not be rendered in export mode", out.contains("data-opentfc-config"));
    }

    @Test
    public void testChartEscapesHtmlInTitle() throws Exception {
        ChartFromTableMacro macro = new ChartFromTableMacro();
        Map<String, String> params = new HashMap<>();
        params.put("chartTitle", "<script>alert(1)</script>");
        params.put("chartType", "pie");

        String out = macro.execute(params, "", contextWithOutputType(null));
        assertFalse("Raw <script> must never appear in output", out.contains("<script>"));
        assertTrue(out.contains("&lt;script&gt;"));

        Element root = parseRoot(out);
        Map<String, Object> cfg = readConfig(root);
        assertEquals("pie", cfg.get("chartType"));
        assertEquals("<script>alert(1)</script>", cfg.get("chartTitle"));
    }

    @Test
    public void testChartExportModeReturnsPlaceholder() throws Exception {
        ChartFromTableMacro macro = new ChartFromTableMacro();
        String out = macro.execute(new HashMap<>(), "", contextWithOutputType("word"));
        assertTrue(out.contains("opentfc-export-chart-fallback"));
    }

    @Test
    public void testTransformerDefaultRender() throws Exception {
        TableTransformerMacro macro = new TableTransformerMacro();
        String out = macro.execute(new HashMap<>(), "<table></table>", contextWithOutputType(null));
        Element root = parseRoot(out);
        Map<String, Object> cfg = readConfig(root);
        assertEquals("SELECT * FROM T1", cfg.get("sqlQuery"));
        assertEquals("", cfg.get("preset"));
    }

    @Test
    public void testPivotDefaultRender() throws Exception {
        PivotTableMacro macro = new PivotTableMacro();
        String out = macro.execute(new HashMap<>(), "<table></table>", contextWithOutputType(null));
        Element root = parseRoot(out);
        Map<String, Object> cfg = readConfig(root);
        assertEquals("sum", cfg.get("aggregateFunction"));
        assertEquals("", cfg.get("rowFields"));
    }

    @Test
    public void testSpreadsheetDefaultRender() throws Exception {
        TableSpreadsheetMacro macro = new TableSpreadsheetMacro();
        String out = macro.execute(new HashMap<>(), null, contextWithOutputType(null));
        Element root = parseRoot(out);
        Map<String, Object> cfg = readConfig(root);
        assertEquals("550px", cfg.get("height"));
        assertEquals(Boolean.FALSE, cfg.get("readOnly"));
        assertNotNull("attachmentName must default to a value", cfg.get("attachmentName"));
    }

    @Test
    public void testAttachmentTableRendersHeaderWhenNoAttachments() throws Exception {
        AttachmentManager attachmentManager = mock(AttachmentManager.class);
        Page page = mock(Page.class);
        when(attachmentManager.getLatestVersionsOfAttachments(page)).thenReturn(Collections.emptyList());

        ConversionContext ctx = mock(ConversionContext.class);
        when(ctx.getEntity()).thenReturn(page);

        AttachmentTableMacro macro = new AttachmentTableMacro(attachmentManager);
        String out = macro.execute(new HashMap<>(), null, ctx);

        assertTrue(out.contains("confluenceTable"));
        assertTrue(out.contains("<thead>"));
        assertTrue(out.contains("File Name"));
        assertTrue(out.contains("</tbody>"));
        assertFalse(out.contains("File Name</a>")); // no data rows
    }

    @Test
    public void testCsvMacroEmbedsSourceConfig() throws Exception {
        CsvTableMacro macro = new CsvTableMacro();
        Map<String, String> params = new HashMap<>();
        params.put("sourceType", "attachment");
        params.put("attachmentName", "data.csv");
        params.put("delimiter", ";");

        String out = macro.execute(params, null, contextWithOutputType(null));
        Element root = parseRoot(out);
        Map<String, Object> cfg = readConfig(root);
        assertEquals("attachment", cfg.get("sourceType"));
        assertEquals("data.csv", cfg.get("attachmentName"));
        assertEquals(";", cfg.get("delimiter"));
    }

    @Test
    public void testMacroIdIsStableAcrossRenders() throws Exception {
        // When macroId is supplied via parameters, output must keep it stable.
        ChartFromTableMacro macro = new ChartFromTableMacro();
        Map<String, String> params = new HashMap<>();
        params.put("macroId", "stable-chart");
        String out1 = macro.execute(params, "", contextWithOutputType(null));
        String out2 = macro.execute(params, "", contextWithOutputType(null));
        assertEquals(parseRoot(out1).id(), parseRoot(out2).id());
    }
}
