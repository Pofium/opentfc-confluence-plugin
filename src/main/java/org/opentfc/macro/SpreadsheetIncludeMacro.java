package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Macro including a range or sheet from another spreadsheet.
 */
public class SpreadsheetIncludeMacro extends BaseOpenTfcMacro {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public BodyType getBodyType() {
        return BodyType.NONE;
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext conversionContext)
            throws MacroExecutionException {

        if (isExportMode(conversionContext)) {
            return "<div class=\"opentfc-export-spreadsheet-include-fallback\">[OpenTFC Spreadsheet Include]</div>";
        }

        String macroId = getOrCreateMacroId(parameters);
        String targetPageId = parameters.getOrDefault("pageId", "");
        String sheetName = parameters.getOrDefault("sheetName", "");
        String range = parameters.getOrDefault("range", "A1:Z100");

        Map<String, Object> config = new HashMap<>();
        config.put("macroId", macroId);
        config.put("targetPageId", targetPageId);
        config.put("sheetName", sheetName);
        config.put("range", range);

        String configJson = "{}";
        try {
            configJson = objectMapper.writeValueAsString(config);
        } catch (Exception ignored) {}

        return "<div class=\"opentfc-container opentfc-spreadsheet-include\" id=\"" +
                escape(macroId) + "\" data-opentfc-config=\"" + escape(configJson) + "\">" +
                "<div class=\"opentfc-sheet-include-view\"></div></div>";
    }
}
