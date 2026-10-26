package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Macro rendering JSON data structures as a Confluence table.
 */
public class JsonTableMacro extends BaseOpenTfcMacro {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public BodyType getBodyType() {
        return BodyType.PLAIN_TEXT;
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext conversionContext)
            throws MacroExecutionException {

        String macroId = getOrCreateMacroId(parameters);
        String sourceType = parameters.getOrDefault("sourceType", "attachment");
        String attachmentName = parameters.getOrDefault("attachmentName", "");
        String url = parameters.getOrDefault("url", "");
        String jsonPath = parameters.getOrDefault("jsonPath", "$");

        long pageId = conversionContext.getEntity() != null ? conversionContext.getEntity().getId() : 0;

        Map<String, Object> config = new HashMap<>();
        config.put("macroId", macroId);
        config.put("pageId", pageId);
        config.put("sourceType", sourceType);
        config.put("attachmentName", attachmentName);
        config.put("url", url);
        config.put("jsonPath", jsonPath);

        String configJson = "{}";
        try {
            configJson = objectMapper.writeValueAsString(config);
        } catch (Exception ignored) {}

        return "<div class=\"opentfc-container opentfc-json\" id=\"" +
                escape(macroId) + "\" data-opentfc-config=\"" + escape(configJson) + "\">" +
                "<div class=\"opentfc-json-grid\"></div></div>";
    }
}
