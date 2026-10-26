package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pivot Table macro: groups and aggregates the body table or an upstream macro on the client.
 */
public class PivotTableMacro extends BaseOpenTfcMacro {

    public PivotTableMacro() {
        super(null);
    }

    public PivotTableMacro(@ComponentImport XhtmlContent xhtmlContent) {
        super(xhtmlContent);
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext ctx)
            throws MacroExecutionException {
        String renderedBody = renderBody(body, ctx);
        if (isExportMode(ctx)) {
            return "<div class=\"opentfc-export-pivot\">" + renderedBody + "</div>";
        }

        String macroId = getOrCreateMacroId(parameters);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("macroId", macroId);
        config.put("sourceMacroId", parameters.getOrDefault("sourceMacroId", ""));
        config.put("rowFields", parameters.getOrDefault("rowFields", ""));
        config.put("columnFields", parameters.getOrDefault("columnFields", ""));
        config.put("valueFields", parameters.getOrDefault("valueFields", ""));
        config.put("aggregateFunction", parameters.getOrDefault("aggregateFunction", "sum"));

        String inner = "<div class=\"opentfc-pivot-controls\"></div>"
                + "<div class=\"opentfc-pivot-grid\"></div>"
                + "<div class=\"opentfc-pivot-source\" style=\"display:none;\">" + renderedBody + "</div>";
        return container("opentfc-pivot", macroId, config, inner);
    }
}
