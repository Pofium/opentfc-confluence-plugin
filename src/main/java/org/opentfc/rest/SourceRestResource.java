package org.opentfc.rest;

import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.opentfc.model.TableModel;
import org.opentfc.service.DataSourceResolver;
import org.opentfc.service.OpenTfcSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;

/**
 * REST endpoint for resolving external and attachment table sources.
 */
@Path("/source")
@Produces(MediaType.APPLICATION_JSON)
public class SourceRestResource {
    private static final Logger log = LoggerFactory.getLogger(SourceRestResource.class);

    private final PageManager pageManager;
    private final AttachmentManager attachmentManager;
    private final OpenTfcSettings settings;
    private final DataSourceResolver resolver = new DataSourceResolver();

    public SourceRestResource(PageManager pageManager, AttachmentManager attachmentManager) {
        this(pageManager, attachmentManager, null);
    }

    public SourceRestResource(@ComponentImport PageManager pageManager,
                              @ComponentImport AttachmentManager attachmentManager,
                              @ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pageManager = pageManager;
        this.attachmentManager = attachmentManager;
        this.settings = pluginSettingsFactory != null ? new OpenTfcSettings(pluginSettingsFactory) : null;
    }

    @GET
    @Path("/{pageId}/{macroId}")
    public Response getSourceData(@PathParam("pageId") long pageId,
                                  @PathParam("macroId") String macroId,
                                  @QueryParam("type") @DefaultValue("csv") String type,
                                  @QueryParam("attachmentName") String attachmentName,
                                  @QueryParam("url") String url,
                                  @QueryParam("encoding") @DefaultValue("UTF-8") String encoding,
                                  @QueryParam("delimiter") @DefaultValue("auto") String delimiter) {

        try {
            String dataText = null;
            if (attachmentName != null && !attachmentName.isEmpty() && pageManager != null) {
                Page page = pageManager.getPage(pageId);
                if (page != null && attachmentManager != null) {
                    Attachment attachment = attachmentManager.getAttachment(page, attachmentName);
                    if (attachment != null) {
                        try (InputStream in = attachmentManager.getAttachmentData(attachment)) {
                            byte[] bytes = DataSourceResolver.readLimited(in, DataSourceResolver.MAX_FETCH_SIZE);
                            dataText = resolver.decode(bytes, encoding);
                        }
                    }
                }
            } else if (url != null && !url.isEmpty()) {
                java.util.List<String> wl = settings != null ? settings.getUrlWhitelist() : java.util.List.of("*");
                byte[] bytes = resolver.fetchSafeUrl(url, wl);
                dataText = resolver.decode(bytes, encoding);
            }

            if (dataText == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Source data stream could not be opened").build();
            }

            TableModel model;
            if ("json".equalsIgnoreCase(type)) {
                model = resolver.parseJson(dataText, "$");
            } else {
                model = resolver.parseCsv(dataText, delimiter);
            }

            model.setId(macroId);
            return Response.ok(model).build();
        } catch (Exception e) {
            log.error("Failed to retrieve source data: {}", e.getMessage(), e);
            return Response.serverError().entity("Error loading source: " + e.getMessage()).build();
        }
    }
}
