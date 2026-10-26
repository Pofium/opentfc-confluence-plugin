package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Macro providing interactive table filtering, sorting, column hiding, and totals.
 */
public class TableFilterMacro extends BaseOpenTfcMacro {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext conversionContext)
            throws MacroExecutionException {

        // Fallback for static exports (PDF, Word, Email)
        if (isExportMode(conversionContext)) {
            return "<div class=\"opentfc-export-table\">" + (body != null ? body : "") + "</div>";
        }

        String macroId = getOrCreateMacroId(parameters);
        String pageSize = parameters.getOrDefault("pageSize", "25");
        boolean freezeHeader = Boolean.parseBoolean(parameters.getOrDefault("freezeHeader", "true"));
        int freezeColumns = 0;
        try {
            freezeColumns = Integer.parseInt(parameters.getOrDefault("freezeColumns", "0"));
        } catch (NumberFormatException ignored) {}

        boolean filterStatus = Boolean.parseBoolean(parameters.getOrDefault("filterStatus", "true"));
        boolean totalsRow = Boolean.parseBoolean(parameters.getOrDefault("totalsRow", "false"));
        boolean enablePagination = Boolean.parseBoolean(parameters.getOrDefault("enablePagination", "true"));
        String hideableColumns = parameters.getOrDefault("hideableColumns", "");

        Map<String, Object> config = new HashMap<>();
        config.put("macroId", macroId);
        config.put("pageSize", pageSize);
        config.put("freezeHeader", freezeHeader);
        config.put("freezeColumns", freezeColumns);
        config.put("filterStatus", filterStatus);
        config.put("totalsRow", totalsRow);
        config.put("enablePagination", enablePagination);
        config.put("hideableColumns", hideableColumns);

        String configJson = "{}";
        try {
            configJson = objectMapper.writeValueAsString(config);
        } catch (Exception ignored) {}

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"opentfc-container opentfc-table-filter\" id=\"")
            .append(escape(macroId))
            .append("\" data-opentfc-config=\"")
            .append(escape(configJson))
            .append("\">");

        // Action Toolbar
        html.append("<div class=\"opentfc-toolbar\"></div>");

        // Table Content Wrapper
        html.append("<div class=\"opentfc-table-wrapper\">");
        html.append(body != null ? body : "");
        html.append("</div>");

        // Pagination Bar
        if (enablePagination) {
            html.append("<div class=\"opentfc-pagination-bar\"></div>");
        }

        html.append("</div>");
        return html.toString();
    }
}
