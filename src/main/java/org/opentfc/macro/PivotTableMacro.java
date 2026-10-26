package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Macro rendering dynamic pivot tables with aggregations.
 */
public class PivotTableMacro extends BaseOpenTfcMacro {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext conversionContext)
            throws MacroExecutionException {

        if (isExportMode(conversionContext)) {
            return "<div class=\"opentfc-export-pivot-fallback\">[OpenTFC Pivot Table]</div>";
        }

        String macroId = getOrCreateMacroId(parameters);
        String sourceMacroId = parameters.getOrDefault("sourceMacroId", "");
        String rowFields = parameters.getOrDefault("rowFields", "");
        String columnFields = parameters.getOrDefault("columnFields", "");
        String valueFields = parameters.getOrDefault("valueFields", "");
        String aggregateFunction = parameters.getOrDefault("aggregateFunction", "sum");

        Map<String, Object> config = new HashMap<>();
        config.put("macroId", macroId);
        config.put("sourceMacroId", sourceMacroId);
        config.put("rowFields", rowFields);
        config.put("columnFields", columnFields);
        config.put("valueFields", valueFields);
        config.put("aggregateFunction", aggregateFunction);

        String configJson = "{}";
        try {
            configJson = objectMapper.writeValueAsString(config);
        } catch (Exception ignored) {}

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"opentfc-container opentfc-pivot\" id=\"")
            .append(escape(macroId))
            .append("\" data-opentfc-config=\"")
            .append(escape(configJson))
            .append("\">");

        // Pivot controls
        html.append("<div class=\"opentfc-pivot-controls\"></div>");

        // Pivot output grid
        html.append("<div class=\"opentfc-pivot-grid\"></div>");

        if (body != null && !body.trim().isEmpty()) {
            html.append("<div class=\"opentfc-pivot-source\" style=\"display:none;\">")
                .append(body)
                .append("</div>");
        }

        html.append("</div>");
        return html.toString();
    }
}
