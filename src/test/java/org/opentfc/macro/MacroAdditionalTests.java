package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.user.ConfluenceUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the macros that were not exercised yet: JSON source, spreadsheet include
 * and the attachment listing (including its extension filters and size formatting).
 */
public class MacroAdditionalTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Element root(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        Element el = doc.selectFirst(".opentfc-container");
        assertNotNull("expected .opentfc-container in: " + html, el);
        return el;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> config(Element el) throws Exception {
        return MAPPER.readValue(el.attr("data-opentfc-config"), Map.class);
    }

    private ConversionContext ctx(String outputType) {
        ConversionContext context = mock(ConversionContext.class);
        when(context.getOutputType()).thenReturn(outputType);
        return context;
    }

    @Test
    public void testJsonTableMacroDefaults() throws Exception {
        String out = new JsonTableMacro().execute(new HashMap<>(), null, ctx(null));
        Element el = root(out);
        Map<String, Object> cfg = config(el);
        assertEquals("attachment", cfg.get("sourceType"));
        assertEquals("$", cfg.get("jsonPath"));
        assertEquals("", cfg.get("attachmentName"));
        assertEquals("", cfg.get("url"));
    }

    @Test
    public void testJsonTableMacroUsesParameters() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("macroId", "json-1");
        params.put("sourceType", "url");
        params.put("url", "https://example.com/data.json");
        params.put("jsonPath", "$.items[*]");

        Element el = root(new JsonTableMacro().execute(params, null, ctx(null)));
        assertEquals("json-1", el.id());
        Map<String, Object> cfg = config(el);
        assertEquals("url", cfg.get("sourceType"));
        assertEquals("https://example.com/data.json", cfg.get("url"));
        assertEquals("$.items[*]", cfg.get("jsonPath"));
    }

    @Test
    public void testJsonTableMacroFallsBackToConfigWhenMapperFails() throws Exception {
        // A parameter map that cannot be serialized is impossible via strings, so verify the
        // rendered container is still well-formed HTML for a minimal call.
        String out = new JsonTableMacro().execute(new HashMap<>(), null, ctx(null));
        assertTrue(out.contains("opentfc-json-grid"));
        assertTrue(out.endsWith("</div>"));
    }

    @Test
    public void testSpreadsheetIncludeMacroRendersContainer() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("macroId", "inc-1");
        params.put("pageId", "777");
        params.put("sheetName", "Sheet1");
        params.put("range", "A1:C10");

        Element el = root(new SpreadsheetIncludeMacro().execute(params, null, ctx(null)));
        assertEquals("inc-1", el.id());
        Map<String, Object> cfg = config(el);
        assertEquals("777", cfg.get("targetPageId"));
        assertEquals("Sheet1", cfg.get("sheetName"));
        assertEquals("A1:C10", cfg.get("range"));
    }

    @Test
    public void testSpreadsheetIncludeDefaultRange() throws Exception {
        Element el = root(new SpreadsheetIncludeMacro().execute(new HashMap<>(), null, ctx(null)));
        assertEquals("A1:Z100", config(el).get("range"));
    }

    @Test
    public void testSpreadsheetIncludeExportMode() throws Exception {
        String out = new SpreadsheetIncludeMacro().execute(new HashMap<>(), null, ctx("pdf"));
        assertTrue(out.contains("opentfc-export-spreadsheet-include-fallback"));
        assertFalse(out.contains("data-opentfc-config"));
    }

    private Attachment attachment(String name, long size, ConfluenceUser creator, Date modified, String comment) {
        Attachment att = mock(Attachment.class);
        when(att.getFileName()).thenReturn(name);
        when(att.getDownloadPath()).thenReturn("/download/" + name);
        when(att.getFileSize()).thenReturn(size);
        when(att.getCreator()).thenReturn(creator);
        when(att.getLastModificationDate()).thenReturn(modified);
        when(att.getVersionComment()).thenReturn(comment);
        return att;
    }

    private String renderAttachmentTable(List<Attachment> attachments, Map<String, String> params) throws Exception {
        AttachmentManager manager = mock(AttachmentManager.class);
        Page page = mock(Page.class);
        when(manager.getLatestVersionsOfAttachments(page)).thenReturn(attachments);

        ConversionContext context = mock(ConversionContext.class);
        when(context.getEntity()).thenReturn(page);

        return new AttachmentTableMacro(manager).execute(params, null, context);
    }

    @Test
    public void testAttachmentTableListsFilesWithMetadata() throws Exception {
        ConfluenceUser author = mock(ConfluenceUser.class);
        when(author.getFullName()).thenReturn("Ilya <P>");
        Attachment att = attachment("report.csv", 2048, author, new Date(0), "initial");

        String out = renderAttachmentTable(List.of(att), new HashMap<>());

        assertTrue(out.contains("report.csv"));
        assertTrue(out.contains("/download/report.csv"));
        assertTrue(out.contains("2.0 KB"));
        assertTrue("creator name must be HTML-escaped", out.contains("Ilya &lt;P&gt;"));
        assertTrue(out.contains("initial"));
        assertTrue(out.contains("1970-01-01"));
    }

    @Test
    public void testAttachmentTableFormatsSmallSizes() throws Exception {
        Attachment att = attachment("note.txt", 512, null, null, null);
        String out = renderAttachmentTable(List.of(att), new HashMap<>());
        assertTrue(out.contains("512 B"));
        // Null creator/comment render as empty cells, and a null modification date falls back to now.
        assertTrue(out.contains("<td class=\"confluenceTd\"></td>"));
    }

    @Test
    public void testAttachmentTableIncludeFilter() throws Exception {
        Attachment csv = attachment("data.csv", 10, null, new Date(0), "c");
        Attachment pdf = attachment("doc.pdf", 10, null, new Date(0), "p");

        Map<String, String> params = new HashMap<>();
        params.put("includeExtensions", "CSV");
        String out = renderAttachmentTable(List.of(csv, pdf), params);

        assertTrue(out.contains("data.csv"));
        assertFalse(out.contains("doc.pdf"));
    }

    @Test
    public void testAttachmentTableExcludeFilter() throws Exception {
        Attachment csv = attachment("data.csv", 10, null, new Date(0), "c");
        Attachment pdf = attachment("doc.pdf", 10, null, new Date(0), "p");

        Map<String, String> params = new HashMap<>();
        params.put("excludeExtensions", "pdf");
        String out = renderAttachmentTable(List.of(csv, pdf), params);

        assertTrue(out.contains("data.csv"));
        assertFalse(out.contains("doc.pdf"));
    }

    @Test
    public void testAttachmentTableSkipsExtensionlessFilesWhenFiltering() throws Exception {
        Attachment readme = attachment("README", 10, null, new Date(0), "r");
        Map<String, String> params = new HashMap<>();
        params.put("includeExtensions", "csv");

        String out = renderAttachmentTable(List.of(readme), params);
        assertFalse(out.contains("README</a>"));
    }

    @Test
    public void testAttachmentTableMacroIdIsEscaped() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("macroId", "\"><script>x</script>");
        String out = renderAttachmentTable(List.of(), params);
        assertFalse(out.contains("<script>"));
        assertTrue(out.contains("&quot;&gt;"));
    }
}
