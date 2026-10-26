package org.opentfc.rest;

import org.opentfc.model.TableModel;
import org.opentfc.service.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * REST endpoint for streaming CSV and XLSX file downloads.
 */
@Path("/export")
public class ExportRestResource {
    private static final Logger log = LoggerFactory.getLogger(ExportRestResource.class);

    private final ExportService exportService = new ExportService();

    @POST
    @Path("/csv")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/csv; charset=UTF-8")
    public Response exportCsv(TableModel table) {
        try {
            String csvContent = exportService.exportToCsv(table);
            byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);

            return Response.ok(bytes)
                    .header("Content-Disposition", "attachment; filename=\"export.csv\"")
                    .header("Content-Type", "text/csv; charset=UTF-8")
                    .build();
        } catch (IOException e) {
            log.error("CSV Export failed: {}", e.getMessage(), e);
            return Response.serverError().entity("Export failed: " + e.getMessage()).build();
        }
    }

    @POST
    @Path("/xlsx")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportXlsx(TableModel table) {
        try {
            byte[] bytes = exportService.exportToXlsx(table);
            return Response.ok(bytes)
                    .header("Content-Disposition", "attachment; filename=\"export.xlsx\"")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .build();
        } catch (IOException e) {
            log.error("XLSX Export failed: {}", e.getMessage(), e);
            return Response.serverError().entity("Export failed: " + e.getMessage()).build();
        }
    }
}
