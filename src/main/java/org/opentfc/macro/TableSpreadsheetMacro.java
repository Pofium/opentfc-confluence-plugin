package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Table Spreadsheet macro: editable grid persisted as a JSON attachment on the page.
 */
public class TableSpreadsheetMacro extends BaseOpenTfcMacro {

    public TableSpreadsheetMacro() {
        super(null);
    }

    public TableSpreadsheetMacro(@ComponentImport XhtmlContent xhtmlContent) {
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
            return "<div class=\"opentfc-export-spreadsheet\">[OpenTFC Spreadsheet " + escape(macroId) + "]</div>";
        }

        String height = parameters.getOrDefault("height", "550px").replaceAll("[^0-9a-z%.]", "");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("macroId", macroId);
        config.put("pageId", pageIdOf(ctx));
        config.put("attachmentName", parameters.getOrDefault("attachmentName", "spreadsheet-" + macroId + ".json"));
        config.put("readOnly", Boolean.parseBoolean(parameters.getOrDefault("readOnly", "false")));
        config.put("height", height);

        String inner = "<div class=\"opentfc-sheet-toolbar\"></div>"
                + "<div class=\"opentfc-sheet-viewport\" style=\"width:100%;max-height:" + escape(height)
                + ";overflow:auto;\"></div>";
        return container("opentfc-spreadsheet", macroId, config, inner);
    }
}
