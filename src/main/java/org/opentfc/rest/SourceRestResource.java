package org.opentfc.rest;

import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.opentfc.model.TableModel;
import org.opentfc.service.DataSourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * REST endpoint for resolving external and attachment table sources.
 */
@Path("/source")
@Produces(MediaType.APPLICATION_JSON)
public class SourceRestResource {
    private static final Logger log = LoggerFactory.getLogger(SourceRestResource.class);

    private final PageManager pageManager;
    private final AttachmentManager attachmentManager;
    private final DataSourceResolver resolver = new DataSourceResolver();

    public SourceRestResource(@ComponentImport PageManager pageManager,
                              @ComponentImport AttachmentManager attachmentManager) {
        this.pageManager = pageManager;
        this.attachmentManager = attachmentManager;
    }

    @GET
    @Path("/{pageId}/{macroId}")
    public Response getSourceData(@PathParam("pageId") long pageId,
                                  @PathParam("macroId") String macroId,
                                  @QueryParam("type") @DefaultValue("csv") String type,
                                  @QueryParam("attachmentName") String attachmentName,
                                  @QueryParam("url") String url,
                                  @QueryParam("encoding") @DefaultValue("UTF-8") String encoding,
                                  @QueryParam("delimiter") @DefaultValue(",") String delimiter) {

        try {
            InputStream in = null;
            if (attachmentName != null && !attachmentName.isEmpty() && pageManager != null) {
                Page page = pageManager.getPage(pageId);
                if (page != null && attachmentManager != null) {
                    Attachment attachment = attachmentManager.getAttachment(page, attachmentName);
                    if (attachment != null) {
                        in = attachmentManager.getAttachmentData(attachment);
                    }
                }
            } else if (url != null && !url.isEmpty()) {
                in = resolver.fetchSafeUrl(url);
            }

            if (in == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Source data stream could not be opened").build();
            }

            TableModel model;
            Charset charset = Charset.forName(encoding);
            if ("json".equalsIgnoreCase(type)) {
                model = resolver.parseJson(in);
            } else {
                char delim = delimiter.length() > 0 ? delimiter.charAt(0) : ',';
                model = resolver.parseCsv(in, charset, delim);
            }

            model.setId(macroId);
            return Response.ok(model).build();
        } catch (Exception e) {
            log.error("Failed to retrieve source data: {}", e.getMessage(), e);
            return Response.serverError().entity("Error loading source: " + e.getMessage()).build();
        }
    }
}
