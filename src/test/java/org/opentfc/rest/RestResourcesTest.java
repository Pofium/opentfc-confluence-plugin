package org.opentfc.rest;

import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import org.junit.Test;
import org.opentfc.model.ColumnType;
import org.opentfc.model.ColumnModel;
import org.opentfc.model.RowModel;
import org.opentfc.model.CellModel;
import org.opentfc.model.SqlExecutionResult;
import org.opentfc.model.TableModel;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the REST layer end to end at the Java level: response codes, headers
 * and payloads, including the SSRF guard reached through the source endpoint.
 */
public class RestResourcesTest {

    private static ByteArrayInputStream csvStream(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    private static TableModel twoColumnTable() {
        TableModel table = new TableModel("t");
        table.addColumn(new ColumnModel(0, "Name", ColumnType.TEXT));
        table.addColumn(new ColumnModel(1, "Qty", ColumnType.NUMBER));
        RowModel row = new RowModel();
        row.addCell(new CellModel("Server", "Server", ColumnType.TEXT));
        row.addCell(new CellModel(5, "5", ColumnType.NUMBER));
        table.addRow(row);
        return table;
    }

    // ---------- /config ----------

    @Test
    @SuppressWarnings("unchecked")
    public void testConfigResourceReturnsFeatureFlags() {
        Response response = new ConfigRestResource().getConfig();
        assertEquals(200, response.getStatus());

        Map<String, Object> config = (Map<String, Object>) response.getEntity();
        assertEquals(org.opentfc.service.OpenTfcVersion.VERSION, config.get("version"));
        assertEquals(10, config.get("sqlTimeoutSeconds"));
        assertEquals(50000, config.get("maxResultRows"));
        assertEquals(10, config.get("maxFileSizeMb"));

        Map<String, Boolean> features = (Map<String, Boolean>) config.get("features");
        assertTrue(features.get("tableFilter"));
        assertTrue(features.get("tableTransformer"));
        assertTrue(features.get("tableSpreadsheet"));
    }

    // ---------- /transformer ----------

    @Test
    public void testTransformerRejectsNullRequest() {
        TransformerRestResource resource = new TransformerRestResource();
        Response response = resource.executeQuery(1L, "m", null);
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testTransformerRejectsMalformedBody() {
        Response response = new TransformerRestResource().executeQuery(1L, "m", "{oops");
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testTransformerRejectsMissingSql() throws Exception {
        TransformerRestResource.QueryRequest request = new TransformerRestResource.QueryRequest();
        request.sql = null;
        request.tables = new ArrayList<>();
        Response response = new TransformerRestResource().executeQuery(1L, "m", OpenTfcJson.write(request));
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testTransformerExecutesSelect() throws Exception {
        TransformerRestResource.QueryRequest request = new TransformerRestResource.QueryRequest();
        request.sql = "SELECT Name, CAST(Qty AS INTEGER) AS Qty FROM T1";
        request.tables = List.of(twoColumnTable());

        Response response = new TransformerRestResource().executeQuery(1L, "m", OpenTfcJson.write(request));
        assertEquals(200, response.getStatus());

        SqlExecutionResult result = OpenTfcJson.read((String) response.getEntity(), SqlExecutionResult.class);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTable().getRows().size());
        assertEquals("Server", result.getTable().getRows().get(0).getCells().get(0).getRawValue());
    }

    @Test
    public void testTransformerReturnsErrorPayloadForDdl() throws Exception {
        TransformerRestResource.QueryRequest request = new TransformerRestResource.QueryRequest();
        request.sql = "DROP TABLE T1";
        request.tables = new ArrayList<>();

        Response response = new TransformerRestResource().executeQuery(1L, "m", OpenTfcJson.write(request));
        assertEquals(200, response.getStatus());

        SqlExecutionResult result = OpenTfcJson.read((String) response.getEntity(), SqlExecutionResult.class);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Security violation"));
    }

    // ---------- /export ----------

    @Test
    public void testExportCsvResponse() throws Exception {
        Response response = new ExportRestResource().exportCsv(null, OpenTfcJson.write(twoColumnTable()));
        assertEquals(200, response.getStatus());
        String disposition = String.valueOf(response.getMetadata().getFirst("Content-Disposition"));
        assertTrue(disposition, disposition.startsWith("attachment; filename=\"export.csv\""));

        String body = new String((byte[]) response.getEntity(), StandardCharsets.UTF_8);
        assertTrue(body.contains("Name,Qty"));
        assertTrue(body.contains("Server,5"));
    }

    @Test
    public void testExportXlsxResponse() throws Exception {
        Response response = new ExportRestResource().exportXlsx(null, OpenTfcJson.write(twoColumnTable()));
        assertEquals(200, response.getStatus());
        String disposition = String.valueOf(response.getMetadata().getFirst("Content-Disposition"));
        assertTrue(disposition, disposition.startsWith("attachment; filename=\"export.xlsx\""));

        byte[] body = (byte[]) response.getEntity();
        assertTrue("XLSX payload must be a zip container", body.length > 2 && body[0] == 'P' && body[1] == 'K');
    }

    /**
     * Regression for the failure that made "Export to Excel" download a CSV: the frontend sends
     * its own column/cell objects, and the export had to survive fields it does not model as well
     * as the legacy cell name produced by the platform Jackson.
     */
    @Test
    public void testExportAcceptsFrontendPayloadWithExtraFields() {
        String body = "{\"id\":\"m\",\"totalRows\":1,"
                + "\"columns\":[{\"index\":0,\"name\":\"Name\",\"type\":\"TEXT\",\"visible\":true,\"currencySuffix\":\" ₽\"},"
                + "{\"index\":1,\"name\":\"Qty\",\"type\":\"NUMBER\",\"visible\":true}],"
                + "\"rows\":[{\"cells\":[{\"val\":\"Server\",\"html\":\"Server\",\"type\":\"TEXT\"},"
                + "{\"val\":\"5\",\"html\":\"5\",\"type\":\"NUMBER\"}]}]}";

        Response response = new ExportRestResource().exportXlsx("таблица-1.xlsx", body);
        assertEquals(200, response.getStatus());
        byte[] bytes = (byte[]) response.getEntity();
        assertTrue("XLSX payload must be a zip container", bytes.length > 2 && bytes[0] == 'P' && bytes[1] == 'K');
        String disposition = String.valueOf(response.getMetadata().getFirst("Content-Disposition"));
        assertTrue(disposition, disposition.contains("filename*=UTF-8''"));
    }

    @Test
    public void testExportAcceptsLegacyRawValueCellName() throws Exception {
        String body = "{\"columns\":[{\"index\":0,\"name\":\"Name\",\"type\":\"TEXT\"}],"
                + "\"rows\":[{\"cells\":[{\"rawValue\":\"Old\",\"html\":\"Old\",\"type\":\"TEXT\"}]}]}";
        Response response = new ExportRestResource().exportCsv(null, body);
        assertEquals(200, response.getStatus());
        String csv = new String((byte[]) response.getEntity(), StandardCharsets.UTF_8);
        assertTrue(csv, csv.contains("Old"));
    }

    @Test
    public void testExportReportsBadBodyInsteadOfOpaqueError() {
        Response response = new ExportRestResource().exportXlsx(null, "{not json");
        assertEquals(400, response.getStatus());
        assertTrue(String.valueOf(response.getEntity()),
                String.valueOf(response.getEntity()).contains("Malformed table payload"));
    }

    @Test
    public void testExportFileNameIsSanitized() throws Exception {
        Response response = new ExportRestResource().exportCsv("a\"\r\nX-Injected: 1/b", OpenTfcJson.write(twoColumnTable()));
        String disposition = String.valueOf(response.getMetadata().getFirst("Content-Disposition"));
        // The header must stay a single line: quotes, CR, LF and path separators cannot escape it
        assertFalse(disposition, disposition.contains("\r") || disposition.contains("\n"));
        assertTrue(disposition, disposition.startsWith("attachment; filename=\"aX-Injected: 1b.csv\""));
        assertFalse(disposition, disposition.contains("\"\""));
    }

    @Test
    public void testExportAttachmentHeaderPercentEncodesUnicode() throws Exception {
        Response response = new ExportRestResource().exportCsv("Отчёт", OpenTfcJson.write(twoColumnTable()));
        String disposition = String.valueOf(response.getMetadata().getFirst("Content-Disposition"));
        assertTrue(disposition, disposition.contains("filename*=UTF-8''" + ExportRestResource.percentEncode("Отчёт.csv")));
    }

    // ---------- /source ----------

    @Test
    public void testSourceLoadsCsvAttachment() throws Exception {
        PageManager pageManager = mock(PageManager.class);
        AttachmentManager attachmentManager = mock(AttachmentManager.class);
        Page page = mock(Page.class);
        Attachment attachment = mock(Attachment.class);

        when(pageManager.getPage(42L)).thenReturn(page);
        when(attachmentManager.getAttachment(page, "data.csv")).thenReturn(attachment);
        when(attachmentManager.getAttachmentData(attachment)).thenReturn(csvStream("A,B\n1,2\n"));

        Response response = new SourceRestResource(pageManager, attachmentManager)
                .getSourceData(42L, "macro-1", "csv", "data.csv", null, "UTF-8", ",");

        assertEquals(200, response.getStatus());
        TableModel model = OpenTfcJson.read((String) response.getEntity(), TableModel.class);
        assertEquals("macro-1", model.getId());
        assertEquals(2, model.getColumns().size());
        assertEquals("A", model.getColumns().get(0).getName());
        assertEquals("1", model.getRows().get(0).getCells().get(0).getRawValue());
    }

    /**
     * The cell value must travel under the name the frontend reads. On a real server the platform
     * Jackson serialized the getter name ("rawValue") instead of "val", so every loaded cell
     * rendered as empty.
     */
    @Test
    public void testSourceSerializesCellsUnderTheFrontendName() throws Exception {
        PageManager pageManager = mock(PageManager.class);
        AttachmentManager attachmentManager = mock(AttachmentManager.class);
        Page page = mock(Page.class);
        Attachment attachment = mock(Attachment.class);

        when(pageManager.getPage(42L)).thenReturn(page);
        when(attachmentManager.getAttachment(page, "data.csv")).thenReturn(attachment);
        when(attachmentManager.getAttachmentData(attachment)).thenReturn(csvStream("A,B\n1,2\n"));

        String json = (String) new SourceRestResource(pageManager, attachmentManager)
                .getSourceData(42L, "macro-1", "csv", "data.csv", null, "UTF-8", ",").getEntity();

        assertTrue(json, json.contains("\"val\""));
        assertFalse(json, json.contains("rawValue"));
    }

    @Test
    public void testSourceLoadsJsonAttachment() throws Exception {
        PageManager pageManager = mock(PageManager.class);
        AttachmentManager attachmentManager = mock(AttachmentManager.class);
        Page page = mock(Page.class);
        Attachment attachment = mock(Attachment.class);

        when(pageManager.getPage(7L)).thenReturn(page);
        when(attachmentManager.getAttachment(page, "data.json")).thenReturn(attachment);
        when(attachmentManager.getAttachmentData(attachment))
                .thenReturn(csvStream("[{\"a\":1},{\"a\":2}]"));

        Response response = new SourceRestResource(pageManager, attachmentManager)
                .getSourceData(7L, "macro-2", "json", "data.json", null, "UTF-8", ",");

        assertEquals(200, response.getStatus());
        TableModel model = OpenTfcJson.read((String) response.getEntity(), TableModel.class);
        assertEquals(1, model.getColumns().size());
        assertEquals(2, model.getRows().size());
    }

    @Test
    public void testSourceRespectsSemicolonDelimiterAndEncoding() throws Exception {
        PageManager pageManager = mock(PageManager.class);
        AttachmentManager attachmentManager = mock(AttachmentManager.class);
        Page page = mock(Page.class);
        Attachment attachment = mock(Attachment.class);

        when(pageManager.getPage(1L)).thenReturn(page);
        when(attachmentManager.getAttachment(page, "data.csv")).thenReturn(attachment);
        when(attachmentManager.getAttachmentData(attachment))
                .thenReturn(csvStream("A;B\nx;y\n"));

        Response response = new SourceRestResource(pageManager, attachmentManager)
                .getSourceData(1L, "m", "csv", "data.csv", null, "UTF-8", ";");

        TableModel model = OpenTfcJson.read((String) response.getEntity(), TableModel.class);
        assertEquals("A", model.getColumns().get(0).getName());
        assertEquals("B", model.getColumns().get(1).getName());
    }

    @Test
    public void testSourceReturns404WhenAttachmentMissing() {
        PageManager pageManager = mock(PageManager.class);
        AttachmentManager attachmentManager = mock(AttachmentManager.class);
        Page page = mock(Page.class);

        when(pageManager.getPage(9L)).thenReturn(page);
        when(attachmentManager.getAttachment(page, "missing.csv")).thenReturn(null);

        Response response = new SourceRestResource(pageManager, attachmentManager)
                .getSourceData(9L, "m", "csv", "missing.csv", null, "UTF-8", ",");

        assertEquals(404, response.getStatus());
    }

    @Test
    public void testSourceReturns404WhenPageMissing() {
        PageManager pageManager = mock(PageManager.class);
        AttachmentManager attachmentManager = mock(AttachmentManager.class);
        when(pageManager.getPage(5L)).thenReturn(null);

        Response response = new SourceRestResource(pageManager, attachmentManager)
                .getSourceData(5L, "m", "csv", "a.csv", null, "UTF-8", ",");

        assertEquals(404, response.getStatus());
    }

    @Test
    public void testSourceBlocksSsrfUrl() {
        PageManager pageManager = mock(PageManager.class);
        AttachmentManager attachmentManager = mock(AttachmentManager.class);

        Response response = new SourceRestResource(pageManager, attachmentManager)
                .getSourceData(1L, "m", "csv", null, "http://127.0.0.1/secret.csv", "UTF-8", ",");

        assertEquals(500, response.getStatus());
        // Error message is now generic (no internal details leaked)
        assertTrue(String.valueOf(response.getEntity()).contains("Error loading source data"));
    }

    @Test
    public void testSourceRejectsUnknownEncoding() {
        PageManager pageManager = mock(PageManager.class);
        AttachmentManager attachmentManager = mock(AttachmentManager.class);
        Page page = mock(Page.class);
        Attachment attachment = mock(Attachment.class);

        when(pageManager.getPage(1L)).thenReturn(page);
        when(attachmentManager.getAttachment(page, "a.csv")).thenReturn(attachment);
        when(attachmentManager.getAttachmentData(attachment)).thenReturn(csvStream("A\n1\n"));

        Response response = new SourceRestResource(pageManager, attachmentManager)
                .getSourceData(1L, "m", "csv", "a.csv", null, "NOT-A-CHARSET", ",");

        // Invalid charset now falls back to UTF-8 instead of throwing — returns 200
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testSourceWithNullCollaboratorsReturns404() {
        Response response = new SourceRestResource(null, null)
                .getSourceData(1L, "m", "csv", null, null, "UTF-8", ",");
        assertEquals(404, response.getStatus());
    }
}
