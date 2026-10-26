package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Macro rendering charts based on embedded tables or upstream macro references.
 */
public class ChartFromTableMacro extends BaseOpenTfcMacro {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext conversionContext)
            throws MacroExecutionException {

        if (isExportMode(conversionContext)) {
            return "<div class=\"opentfc-export-chart-fallback\">[OpenTFC Chart: " +
                    escape(parameters.getOrDefault("chartTitle", "Chart")) + "]</div>";
        }

        String macroId = getOrCreateMacroId(parameters);
        String sourceMacroId = parameters.getOrDefault("sourceMacroId", "");
        String chartType = parameters.getOrDefault("chartType", "column");
        String xAxisColumn = parameters.getOrDefault("xAxisColumn", "");
        String yAxisColumns = parameters.getOrDefault("yAxisColumns", "");
        String aggregation = parameters.getOrDefault("aggregation", "none");
        String chartTitle = parameters.getOrDefault("chartTitle", "");
        String height = parameters.getOrDefault("height", "400px");

        Map<String, Object> config = new HashMap<>();
        config.put("macroId", macroId);
        config.put("sourceMacroId", sourceMacroId);
        config.put("chartType", chartType);
        config.put("xAxisColumn", xAxisColumn);
        config.put("yAxisColumns", yAxisColumns);
        config.put("aggregation", aggregation);
        config.put("chartTitle", chartTitle);
        config.put("height", height);

        String configJson = "{}";
        try {
            configJson = objectMapper.writeValueAsString(config);
        } catch (Exception ignored) {}

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"opentfc-container opentfc-chart\" id=\"")
            .append(escape(macroId))
            .append("\" data-opentfc-config=\"")
            .append(escape(configJson))
            .append("\" style=\"min-height: ").append(escape(height)).append(";\">");

        // Toolbar for chart options / export to PNG
        html.append("<div class=\"opentfc-chart-toolbar\"></div>");

        // ECharts rendering canvas
        html.append("<div class=\"opentfc-chart-canvas\" style=\"width: 100%; height: ")
            .append(escape(height)).append(";\"></div>");

        // Hidden source table if provided in body
        if (body != null && !body.trim().isEmpty()) {
            html.append("<div class=\"opentfc-chart-source\" style=\"display:none;\">")
                .append(body)
                .append("</div>");
        }

        html.append("</div>");
        return html.toString();
    }
}
