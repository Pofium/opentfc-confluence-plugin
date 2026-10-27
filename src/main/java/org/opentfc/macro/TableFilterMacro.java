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

        boolean showSearchBox = Boolean.parseBoolean(parameters.getOrDefault("showSearchBox", "true"));
        boolean showColumnFilters = parameters.containsKey("showColumnFilters")
                ? Boolean.parseBoolean(parameters.get("showColumnFilters"))
                : Boolean.parseBoolean(parameters.getOrDefault("filterStatus", "true"));
        boolean stickyHeader = parameters.containsKey("stickyHeader")
                ? Boolean.parseBoolean(parameters.get("stickyHeader"))
                : Boolean.parseBoolean(parameters.getOrDefault("freezeHeader", "true"));

        int freezeColumns = 0;
        try {
            freezeColumns = Integer.parseInt(parameters.getOrDefault("freezeColumns", "0").trim());
        } catch (NumberFormatException ignored) {
            // keep default
        }

        boolean showTotals = parameters.containsKey("showTotals")
                ? Boolean.parseBoolean(parameters.get("showTotals"))
                : Boolean.parseBoolean(parameters.getOrDefault("totalsRow", "false"));
        boolean allowHideColumns = parameters.containsKey("allowHideColumns")
                ? Boolean.parseBoolean(parameters.get("allowHideColumns"))
                : true;
        boolean showExportButtons = Boolean.parseBoolean(parameters.getOrDefault("showExportButtons", "true"));
        String pageSize = parameters.getOrDefault("pageSize", "25");
        boolean enablePagination = Boolean.parseBoolean(parameters.getOrDefault("enablePagination", "true"));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("macroId", macroId);
        config.put("pageId", pageIdOf(ctx));
        config.put("showSearchBox", showSearchBox);
        config.put("showColumnFilters", showColumnFilters);
        config.put("stickyHeader", stickyHeader);
        config.put("freezeColumns", Math.max(0, freezeColumns));
        config.put("showTotals", showTotals);
        config.put("allowHideColumns", allowHideColumns);
        config.put("showExportButtons", showExportButtons);
        config.put("pageSize", pageSize);
        config.put("enablePagination", enablePagination);

        // Legacy compatibility keys
        config.put("freezeHeader", stickyHeader);
        config.put("filterStatus", showColumnFilters);
        config.put("totalsRow", showTotals);
        config.put("hideableColumns", parameters.getOrDefault("hideableColumns", ""));

        String inner = "<div class=\"opentfc-toolbar\"></div>"
                + "<div class=\"opentfc-table-wrapper\">" + renderedBody + "</div>"
                + (enablePagination ? "<div class=\"opentfc-pagination-bar\"></div>" : "");
        return container("opentfc-table-filter", macroId, config, inner);
    }
}
