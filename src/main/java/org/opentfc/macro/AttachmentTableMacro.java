package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import com.atlassian.sal.api.component.ComponentLocator;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Attachment Table macro: renders the current page's attachments as a table
 * (can be wrapped by Table Filter for filtering/sorting).
 */
public class AttachmentTableMacro extends BaseOpenTfcMacro {

    private final AttachmentManager attachmentManager;
    private final ApplicationProperties applicationProperties;

    public AttachmentTableMacro() {
        this(resolve(XhtmlContent.class),
             resolve(AttachmentManager.class),
             resolve(ApplicationProperties.class));
    }

    public AttachmentTableMacro(AttachmentManager attachmentManager) {
        this(null, attachmentManager, null);
    }

    public AttachmentTableMacro(@ComponentImport XhtmlContent xhtmlContent,
                                @ComponentImport AttachmentManager attachmentManager,
                                @ComponentImport ApplicationProperties applicationProperties) {
        super(xhtmlContent);
        this.attachmentManager = attachmentManager;
        this.applicationProperties = applicationProperties;
    }

    private static <T> T resolve(Class<T> type) {
        try {
            return ComponentLocator.getComponent(type);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public BodyType getBodyType() {
        return BodyType.NONE;
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext ctx)
            throws MacroExecutionException {
        String macroId = getOrCreateMacroId(parameters);
        Set<String> include = parseExtensions(parameters.get("includeExtensions"));
        Set<String> exclude = parseExtensions(parameters.get("excludeExtensions"));
        String contextPath = applicationProperties != null ? applicationProperties.getBaseUrl(UrlMode.RELATIVE) : "";

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"opentfc-attachment-table\" id=\"").append(escape(macroId)).append("\">")
            .append("<table class=\"confluenceTable\"><thead><tr>")
            .append("<th class=\"confluenceTh\">File Name</th>")
            .append("<th class=\"confluenceTh\">Size</th>")
            .append("<th class=\"confluenceTh\">Creator</th>")
            .append("<th class=\"confluenceTh\">Modified</th>")
            .append("<th class=\"confluenceTh\">Comment</th>")
            .append("</tr></thead><tbody>");

        ContentEntityObject entity = ctx != null ? ctx.getEntity() : null;
        if (entity != null && attachmentManager != null) {
            List<Attachment> attachments = attachmentManager.getLatestVersionsOfAttachments(entity);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
            for (Attachment att : attachments) {
                String fileName = att.getFileName();
                String ext = extensionOf(fileName);
                if (!include.isEmpty() && !include.contains(ext)) {
                    continue;
                }
                if (exclude.contains(ext)) {
                    continue;
                }
                Date modDate = att.getLastModificationDate() != null ? att.getLastModificationDate() : new Date();
                html.append("<tr>")
                    .append("<td class=\"confluenceTd\"><a href=\"")
                    .append(escape(contextPath + att.getDownloadPath())).append("\">")
                    .append(escape(fileName)).append("</a></td>")
                    .append("<td class=\"confluenceTd\">").append(escape(formatSize(att.getFileSize()))).append("</td>")
                    .append("<td class=\"confluenceTd\">")
                    .append(att.getCreator() != null ? escape(att.getCreator().getFullName()) : "")
                    .append("</td>")
                    .append("<td class=\"confluenceTd\">")
                    .append(sdf.format(modDate))
                    .append("</td>")
                    .append("<td class=\"confluenceTd\">")
                    .append(att.getVersionComment() != null ? escape(att.getVersionComment()) : "")
                    .append("</td>")
                    .append("</tr>");
            }
        }
        html.append("</tbody></table></div>");
        return html.toString();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        } else {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    private Set<String> parseExtensions(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.toLowerCase(Locale.ROOT).split("[,;\\s]+"))
                .map(s -> s.startsWith(".") ? s.substring(1) : s)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String extensionOf(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1).toLowerCase(Locale.ROOT) : "";
    }
}
