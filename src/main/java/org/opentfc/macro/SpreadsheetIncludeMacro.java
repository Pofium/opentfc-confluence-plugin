package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spreadsheet Include macro: read-only view of a spreadsheet (by page id + spreadsheet macroId).
 */
public class SpreadsheetIncludeMacro extends BaseOpenTfcMacro {

    public SpreadsheetIncludeMacro() {
        super(null);
    }

    public SpreadsheetIncludeMacro(@ComponentImport XhtmlContent xhtmlContent) {
        super(xhtmlContent);
    }

    @Override
    public BodyType getBodyType() {
        return BodyType.NONE;
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext ctx)
            throws MacroExecutionException {
        String macroId = getOrCreateMacroId(parameters);
        if (isExportMode(ctx)) {
            return "<div class=\"opentfc-export-spreadsheet-include opentfc-export-spreadsheet-include-fallback\">[OpenTFC Spreadsheet Include]</div>";
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("macroId", macroId);
        config.put("targetPageId", parameters.getOrDefault("pageId", "").replaceAll("[^0-9]", ""));
        config.put("sheetMacroId", parameters.getOrDefault("sheetMacroId", "").replaceAll("[^A-Za-z0-9_-]", ""));
        config.put("sheetName", parameters.get("sheetName"));
        config.put("range", parameters.getOrDefault("range", "A1:Z100"));

        return container("opentfc-spreadsheet-include", macroId, config,
                "<div class=\"opentfc-sheet-include-view\"></div>");
    }
}
