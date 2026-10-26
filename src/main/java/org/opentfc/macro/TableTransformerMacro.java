package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Macro executing SQL transformations on one or more source tables.
 */
public class TableTransformerMacro extends BaseOpenTfcMacro {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext conversionContext)
            throws MacroExecutionException {

        if (isExportMode(conversionContext)) {
            return "<div class=\"opentfc-export-transformer-fallback\">" + (body != null ? body : "") + "</div>";
        }

        String macroId = getOrCreateMacroId(parameters);
        String sourceMacroIds = parameters.getOrDefault("sourceMacroIds", "");
        String sqlQuery = parameters.getOrDefault("sqlQuery", "SELECT * FROM T1");
        String preset = parameters.getOrDefault("preset", "");

        Map<String, Object> config = new HashMap<>();
        config.put("macroId", macroId);
        config.put("sourceMacroIds", sourceMacroIds);
        config.put("sqlQuery", sqlQuery);
        config.put("preset", preset);

        String configJson = "{}";
        try {
            configJson = objectMapper.writeValueAsString(config);
        } catch (Exception ignored) {}

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"opentfc-container opentfc-transformer\" id=\"")
            .append(escape(macroId))
            .append("\" data-opentfc-config=\"")
            .append(escape(configJson))
            .append("\">");

        // Transformer result grid
        html.append("<div class=\"opentfc-transformer-output\"></div>");

        // Sources provided in macro body
        if (body != null && !body.trim().isEmpty()) {
            html.append("<div class=\"opentfc-transformer-sources\" style=\"display:none;\">")
                .append(body)
                .append("</div>");
        }

        html.append("</div>");
        return html.toString();
    }
}
