package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Table Filter macro: wraps tables in its body and makes them filterable/sortable on the client.
 */
public class TableFilterMacro extends BaseOpenTfcMacro {

    public TableFilterMacro() {
        super(null);
    }

    public TableFilterMacro(@ComponentImport XhtmlContent xhtmlContent) {
        super(xhtmlContent);
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext ctx)
            throws MacroExecutionException {
        String renderedBody = renderBody(body, ctx);

        // Static fallback for PDF/Word/e-mail exports
        if (isExportMode(ctx)) {
            return "<div class=\"opentfc-export-table\">" + renderedBody + "</div>";
        }

        String macroId = getOrCreateMacroId(parameters);
        int freezeColumns = 0;
        try {
            freezeColumns = Integer.parseInt(parameters.getOrDefault("freezeColumns", "0").trim());
        } catch (NumberFormatException ignored) {
            // keep default
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("macroId", macroId);
        config.put("pageId", pageIdOf(ctx));
        config.put("pageSize", parameters.getOrDefault("pageSize", "25"));
        config.put("freezeHeader", Boolean.parseBoolean(parameters.getOrDefault("freezeHeader", "true")));
        config.put("freezeColumns", Math.max(0, freezeColumns));
        config.put("filterStatus", Boolean.parseBoolean(parameters.getOrDefault("filterStatus", "true")));
        config.put("totalsRow", Boolean.parseBoolean(parameters.getOrDefault("totalsRow", "false")));
        boolean enablePagination = Boolean.parseBoolean(parameters.getOrDefault("enablePagination", "true"));
        config.put("enablePagination", enablePagination);
        config.put("hideableColumns", parameters.getOrDefault("hideableColumns", ""));

        String inner = "<div class=\"opentfc-toolbar\"></div>"
                + "<div class=\"opentfc-table-wrapper\">" + renderedBody + "</div>"
                + (enablePagination ? "<div class=\"opentfc-pagination-bar\"></div>" : "");
        return container("opentfc-table-filter", macroId, config, inner);
    }
}
