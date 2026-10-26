package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Macro rendering full Excel-compatible spreadsheets directly on Confluence pages.
 */
public class TableSpreadsheetMacro extends BaseOpenTfcMacro {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public BodyType getBodyType() {
        return BodyType.NONE;
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext conversionContext)
            throws MacroExecutionException {

        if (isExportMode(conversionContext)) {
            return "<div class=\"opentfc-export-spreadsheet-fallback\">[OpenTFC Spreadsheet: " +
                    escape(parameters.getOrDefault("attachmentName", "Sheet")) + "]</div>";
        }

        String macroId = getOrCreateMacroId(parameters);
        String attachmentName = parameters.getOrDefault("attachmentName", "spreadsheet-" + macroId + ".xlsx");
        boolean readOnly = Boolean.parseBoolean(parameters.getOrDefault("readOnly", "false"));
        String height = parameters.getOrDefault("height", "550px");

        long pageId = 0;
        if (conversionContext.getEntity() != null) {
            pageId = conversionContext.getEntity().getId();
        }

        Map<String, Object> config = new HashMap<>();
        config.put("macroId", macroId);
        config.put("pageId", pageId);
        config.put("attachmentName", attachmentName);
        config.put("readOnly", readOnly);
        config.put("height", height);

        String configJson = "{}";
        try {
            configJson = objectMapper.writeValueAsString(config);
        } catch (Exception ignored) {}

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"opentfc-container opentfc-spreadsheet\" id=\"")
            .append(escape(macroId))
            .append("\" data-opentfc-config=\"")
            .append(escape(configJson))
            .append("\" style=\"min-height: ").append(escape(height)).append(";\">");

        // Spreadsheet action toolbar
        html.append("<div class=\"opentfc-sheet-toolbar\"></div>");

        // Spreadsheet viewport
        html.append("<div class=\"opentfc-sheet-viewport\" style=\"width:100%; height:")
            .append(escape(height)).append(";\"></div>");

        html.append("</div>");
        return html.toString();
    }
}
