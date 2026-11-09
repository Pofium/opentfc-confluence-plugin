package org.opentfc.rest;

import org.opentfc.diag.OpenTfcLogStore;
import org.opentfc.model.TableModel;
import org.opentfc.service.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;

/**
 * REST endpoint for streaming CSV and XLSX file downloads.
 *
 * Request bodies are taken as raw text and parsed by {@link OpenTfcJson}: letting Jersey bind
 * the model directly would use the platform Jackson copy, which cannot read the annotations
 * of this bundle and rejects every field it does not know (the cause of the old
 * "Export to Excel downloads a CSV" failure).
 */
@Path("/export")
public class ExportRestResource {
    private static final Logger log = LoggerFactory.getLogger(ExportRestResource.class);

    private static final String XLSX_MEDIA = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportService exportService = new ExportService();

    @POST
    @Path("/csv")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/csv; charset=UTF-8")
    public Response exportCsv(@QueryParam("fileName") String fileName, String body) {
        TableModel table;
        try {
            table = OpenTfcJson.readTable(body);
        } catch (Exception e) {
            return badRequest("export-csv", "Malformed table payload for CSV export", e);
        }
        try {
            byte[] bytes = exportService.exportToCsv(table).getBytes(StandardCharsets.UTF_8);
            return Response.ok(bytes)
                    .header("Content-Disposition", attachment(fileName, "export", "csv"))
                    .header("Content-Type", "text/csv; charset=UTF-8")
                    .build();
        } catch (Throwable e) {
            return serverError("export-csv", "CSV export failed", e);
        }
    }

    @POST
    @Path("/xlsx")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(XLSX_MEDIA)
    public Response exportXlsx(@QueryParam("fileName") String fileName, String body) {
        TableModel table;
        try {
            table = OpenTfcJson.readTable(body);
        } catch (Exception e) {
            return badRequest("export-xlsx", "Malformed table payload for Excel export", e);
        }
        try {
            byte[] bytes = exportService.exportToXlsx(table);
            return Response.ok(bytes)
                    .header("Content-Disposition", attachment(fileName, "export", "xlsx"))
                    .header("Content-Type", XLSX_MEDIA)
                    .build();
        } catch (Throwable e) {
            // Throwable, not Exception: a missing/older commons-io or AWT failure inside POI
            // surfaces as an Error, and an unmapped Error becomes an opaque XML 500 that the
            // browser cannot distinguish from a network problem.
            return serverError("export-xlsx", "Excel export failed", e);
        }
    }

    private Response badRequest(String category, String what, Throwable e) {
        log.warn("{}: {}", what, e.getMessage());
        OpenTfcLogStore.warn(category, what + ": " + e.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(what + ": " + reasonOf(e)).type(MediaType.TEXT_PLAIN).build();
    }

    private Response serverError(String category, String what, Throwable e) {
        log.error("{}: {}", what, e.getMessage(), e);
        OpenTfcLogStore.error(category, what, e);
        return Response.serverError().entity(what + ": " + reasonOf(e)).type(MediaType.TEXT_PLAIN).build();
    }

    /** Short, safe cause description for the client (full stack stays in the logs). */
    private static String reasonOf(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        String flat = message.replace('\n', ' ').replace('\r', ' ').trim();
        return flat.length() > 200 ? flat.substring(0, 200) + "..." : flat;
    }

    /** RFC 5987 attachment header; the name is sanitized so a crafted query cannot inject headers. */
    static String attachment(String requested, String defaultBase, String extension) {
        String base = sanitizeFileName(requested);
        if (base.isEmpty()) {
            base = defaultBase;
        }
        return "attachment; filename=\"" + base + "." + extension + "\"; filename*=UTF-8''"
                + percentEncode(base + "." + extension);
    }

    private static String sanitizeFileName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[\\r\\n\\\"\\\\/\\u0000-\\u001F]", "").trim();
        // Drop a trailing extension if the caller supplied one; it is appended here.
        cleaned = cleaned.replaceAll("(?i)\\.(csv|xlsx)$", "");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    static String percentEncode(String value) {
        StringBuilder sb = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            boolean unreserved = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '~' || c == '-';
            if (unreserved) {
                sb.append(c);
            } else {
                sb.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return sb.toString();
    }
}
