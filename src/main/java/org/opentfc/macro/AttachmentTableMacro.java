package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Macro listing page attachments as a rich filterable table.
 */
public class AttachmentTableMacro extends BaseOpenTfcMacro {

    private final AttachmentManager attachmentManager;

    public AttachmentTableMacro(@ComponentImport AttachmentManager attachmentManager) {
        this.attachmentManager = attachmentManager;
    }

    @Override
    public BodyType getBodyType() {
        return BodyType.NONE;
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext conversionContext)
            throws MacroExecutionException {

        String macroId = getOrCreateMacroId(parameters);
        String includeExtensions = parameters.getOrDefault("includeExtensions", "").toLowerCase();
        String excludeExtensions = parameters.getOrDefault("excludeExtensions", "").toLowerCase();

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"opentfc-attachment-table-wrapper\" id=\"").append(escape(macroId)).append("\">");
        html.append("<table class=\"confluenceTable tablesorter opentfc-data-table\">");
        html.append("<thead><tr>");
        html.append("<th class=\"confluenceTh\">File Name</th>");
        html.append("<th class=\"confluenceTh\">Size</th>");
        html.append("<th class=\"confluenceTh\">Creator</th>");
        html.append("<th class=\"confluenceTh\">Last Modified</th>");
        html.append("<th class=\"confluenceTh\">Comment</th>");
        html.append("</tr></thead>");
        html.append("<tbody>");

        if (conversionContext.getEntity() instanceof Page && attachmentManager != null) {
            Page page = (Page) conversionContext.getEntity();
            List<Attachment> attachments = attachmentManager.getLatestVersionsOfAttachments(page);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

            for (Attachment att : attachments) {
                String fileName = att.getFileName();
                String ext = getFileExtension(fileName).toLowerCase();

                if (!includeExtensions.isEmpty() && (ext.isEmpty() || !includeExtensions.contains(ext))) {
                    continue;
                }
                if (!excludeExtensions.isEmpty() && excludeExtensions.contains(ext)) {
                    continue;
                }

                html.append("<tr>");
                html.append("<td class=\"confluenceTd\"><a href=\"")
                    .append(att.getDownloadPath())
                    .append("\">")
                    .append(escape(fileName))
                    .append("</a></td>");
                html.append("<td class=\"confluenceTd\">").append(formatFileSize(att.getFileSize())).append("</td>");
                html.append("<td class=\"confluenceTd\">")
                    .append(att.getCreator() != null ? escape(att.getCreator().getFullName()) : "")
                    .append("</td>");
                html.append("<td class=\"confluenceTd\">")
                    .append(sdf.format(att.getLastModificationDate() != null ? att.getLastModificationDate() : new Date()))
                    .append("</td>");
                html.append("<td class=\"confluenceTd\">")
                    .append(escape(att.getVersionComment()))
                    .append("</td>");
                html.append("</tr>");
            }
        }

        html.append("</tbody></table></div>");
        return html.toString();
    }

    private String getFileExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1) : "";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        // Locale-independent: file sizes must not switch to a decimal comma under ru_RU etc.
        return String.format(java.util.Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
