package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Table Transformer macro: runs SQL (DuckDB, server-side) over tables T1..Tn found in its body.
 */
public class TableTransformerMacro extends BaseOpenTfcMacro {

    public TableTransformerMacro() {
        super(null);
    }

    public TableTransformerMacro(@ComponentImport XhtmlContent xhtmlContent) {
        super(xhtmlContent);
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext ctx)
            throws MacroExecutionException {
        String renderedBody = renderBody(body, ctx);
        if (isExportMode(ctx)) {
            return "<div class=\"opentfc-export-transformer\">" + renderedBody + "</div>";
        }

        String macroId = getOrCreateMacroId(parameters);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("macroId", macroId);
        config.put("pageId", pageIdOf(ctx));
        config.put("sourceMacroIds", parameters.getOrDefault("sourceMacroIds", ""));
        config.put("sqlQuery", parameters.getOrDefault("sqlQuery", "SELECT * FROM T1"));
        config.put("preset", parameters.getOrDefault("preset", ""));

        String inner = "<div class=\"opentfc-transformer-output\"></div>"
                + "<div class=\"opentfc-transformer-sources\" style=\"display:none;\">" + renderedBody + "</div>";
        return container("opentfc-transformer", macroId, config, inner);
    }
}
