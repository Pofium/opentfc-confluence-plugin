package org.opentfc.rest;

import com.atlassian.plugins.rest.common.security.AnonymousAllowed;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

/**
 * REST endpoint returning client-side plugin configuration and limits.
 */
@Path("/config")
@Produces(MediaType.APPLICATION_JSON)
public class ConfigRestResource {

    @GET
    @AnonymousAllowed
    public Response getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("version", "1.0.2");
        config.put("sqlTimeoutSeconds", 10);
        config.put("maxResultRows", 50000);
        config.put("maxFileSizeMb", 10);
        config.put("features", Map.of(
                "tableFilter", true,
                "chartFromTable", true,
                "pivotTable", true,
                "tableTransformer", true,
                "tableSpreadsheet", true,
                "csvTable", true,
                "jsonTable", true
        ));
        return Response.ok(config).build();
    }
}
