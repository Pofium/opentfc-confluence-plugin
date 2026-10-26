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
        assertEquals("1.0.0", config.get("version"));
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
    public void testTransformerRejectsMissingSql() {
        TransformerRestResource.QueryRequest request = new TransformerRestResource.QueryRequest();
        request.sql = null;
        Response response = new TransformerRestResource().executeQuery(1L, "m", request);
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testTransformerExecutesSelect() {
        TransformerRestResource.QueryRequest request = new TransformerRestResource.QueryRequest();
        request.sql = "SELECT Name, CAST(Qty AS INTEGER) AS Qty FROM T1";
        request.tables = List.of(twoColumnTable());

        Response response = new TransformerRestResource().executeQuery(1L, "m", request);
        assertEquals(200, response.getStatus());

        SqlExecutionResult result = (SqlExecutionResult) response.getEntity();
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTable().getRows().size());
        assertEquals("Server", result.getTable().getRows().get(0).getCells().get(0).getRawValue());
    }

    @Test
    public void testTransformerReturnsErrorPayloadForDdl() {
        TransformerRestResource.QueryRequest request = new TransformerRestResource.QueryRequest();
        request.sql = "DROP TABLE T1";
        request.tables = new ArrayList<>();

        Response response = new TransformerRestResource().executeQuery(1L, "m", request);
        assertEquals(200, response.getStatus());

        SqlExecutionResult result = (SqlExecutionResult) response.getEntity();
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Security violation"));
    }

    // ---------- /export ----------

    @Test
    public void testExportCsvResponse() {
        Response response = new ExportRestResource().exportCsv(twoColumnTable());
        assertEquals(200, response.getStatus());
        assertEquals("attachment; filename=\"export.csv\"", String.valueOf(response.getMetadata().getFirst("Content-Disposition")));

        String body = new String((byte[]) response.getEntity(), StandardCharsets.UTF_8);
        assertTrue(body.contains("Name,Qty"));
        assertTrue(body.contains("Server,5"));
    }

    @Test
    public void testExportXlsxResponse() {
        Response response = new ExportRestResource().exportXlsx(twoColumnTable());
        assertEquals(200, response.getStatus());
        assertEquals("attachment; filename=\"export.xlsx\"", String.valueOf(response.getMetadata().getFirst("Content-Disposition")));

        byte[] body = (byte[]) response.getEntity();
        assertTrue("XLSX payload must be a zip container", body.length > 2 && body[0] == 'P' && body[1] == 'K');
    }

    // ---------- /source ----------

    @Test
    public void testSourceLoadsCsvAttachment() {
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
        TableModel model = (TableModel) response.getEntity();
        assertEquals("macro-1", model.getId());
        assertEquals(2, model.getColumns().size());
        assertEquals("A", model.getColumns().get(0).getName());
        assertEquals("1", model.getRows().get(0).getCells().get(0).getRawValue());
    }

    @Test
    public void testSourceLoadsJsonAttachment() {
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
        TableModel model = (TableModel) response.getEntity();
        assertEquals(1, model.getColumns().size());
        assertEquals(2, model.getRows().size());
    }

    @Test
    public void testSourceRespectsSemicolonDelimiterAndEncoding() {
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

        TableModel model = (TableModel) response.getEntity();
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
        assertTrue(String.valueOf(response.getEntity()).contains("SSRF"));
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

        assertEquals(500, response.getStatus());
    }

    @Test
    public void testSourceWithNullCollaboratorsReturns404() {
        Response response = new SourceRestResource(null, null)
                .getSourceData(1L, "m", "csv", null, null, "UTF-8", ",");
        assertEquals(404, response.getStatus());
    }
}
