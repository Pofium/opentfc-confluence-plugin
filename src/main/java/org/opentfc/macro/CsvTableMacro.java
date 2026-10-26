package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Macro rendering external or attached CSV/TSV data as a Confluence table.
 */
public class CsvTableMacro extends BaseOpenTfcMacro {

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
        String delimiter = parameters.getOrDefault("delimiter", "auto");
        String encoding = parameters.getOrDefault("encoding", "UTF-8");

        long pageId = conversionContext.getEntity() != null ? conversionContext.getEntity().getId() : 0;

        Map<String, Object> config = new HashMap<>();
        config.put("macroId", macroId);
        config.put("pageId", pageId);
        config.put("sourceType", sourceType);
        config.put("attachmentName", attachmentName);
        config.put("url", url);
        config.put("delimiter", delimiter);
        config.put("encoding", encoding);

        String configJson = "{}";
        try {
            configJson = objectMapper.writeValueAsString(config);
        } catch (Exception ignored) {}

        return "<div class=\"opentfc-container opentfc-csv\" id=\"" +
                escape(macroId) + "\" data-opentfc-config=\"" + escape(configJson) + "\">" +
                "<div class=\"opentfc-csv-grid\"></div></div>";
    }
}
