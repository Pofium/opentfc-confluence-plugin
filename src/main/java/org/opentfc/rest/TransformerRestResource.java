package org.opentfc.rest;

import org.opentfc.diag.OpenTfcLogStore;
import org.opentfc.model.SqlExecutionResult;
import org.opentfc.model.TableModel;
import org.opentfc.service.SqlTransformerEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for executing SQL queries on source tables via DuckDB.
 */
@Path("/transformer")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TransformerRestResource {
    private static final Logger log = LoggerFactory.getLogger(TransformerRestResource.class);

    private final SqlTransformerEngine sqlEngine = new SqlTransformerEngine();

    public static class QueryRequest {
        public String sql;
        public List<TableModel> tables;
    }

    @POST
    @Path("/{pageId}/{macroId}/query")
    public Response executeQuery(@PathParam("pageId") long pageId,
                                 @PathParam("macroId") String macroId,
                                 QueryRequest request) {
        if (request == null || request.sql == null) {
            OpenTfcLogStore.warn("rest-transformer", "Rejected query: SQL body is missing (page=" + pageId + ")");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "SQL query must be provided"))
                    .build();
        }

        OpenTfcLogStore.info("rest-transformer", "SQL query: page=" + pageId + " macro=" + macroId
                + " tables=" + (request.tables != null ? request.tables.size() : 0)
                + " len=" + request.sql.length());
        SqlExecutionResult result = sqlEngine.executeQuery(request.sql, request.tables != null ? request.tables : List.of());
        if (result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()) {
            OpenTfcLogStore.warn("rest-transformer", "Query failed on page=" + pageId + ": " + result.getErrorMessage());
        }
        return Response.ok(result).build();
    }
}
