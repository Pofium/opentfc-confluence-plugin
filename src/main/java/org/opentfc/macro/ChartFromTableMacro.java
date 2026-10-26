package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chart from Table macro: renders an ECharts chart from the body table or an upstream macro.
 */
public class ChartFromTableMacro extends BaseOpenTfcMacro {

    public ChartFromTableMacro() {
        super(null);
    }

    public ChartFromTableMacro(@ComponentImport XhtmlContent xhtmlContent) {
        super(xhtmlContent);
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext ctx)
            throws MacroExecutionException {
        String renderedBody = renderBody(body, ctx);
        String title = parameters.getOrDefault("chartTitle", "");

        // In exports show the source data table (charts need JS)
        if (isExportMode(ctx)) {
            return "<div class=\"opentfc-export-chart opentfc-export-chart-fallback\"><p><strong>" + escape(title) + "</strong></p>"
                    + renderedBody + "</div>";
        }

        String macroId = getOrCreateMacroId(parameters);
        String height = parameters.getOrDefault("height", "400px").replaceAll("[^0-9a-z%.]", "");

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("macroId", macroId);
        config.put("sourceMacroId", parameters.getOrDefault("sourceMacroId", ""));
        config.put("chartType", parameters.getOrDefault("chartType", "column"));
        config.put("xAxisColumn", parameters.getOrDefault("xAxisColumn", ""));
        config.put("yAxisColumns", parameters.getOrDefault("yAxisColumns", ""));
        config.put("aggregation", parameters.getOrDefault("aggregation", "none"));
        config.put("chartTitle", title);
        config.put("height", height);

        String inner = "<div class=\"opentfc-chart-toolbar\"></div>"
                + "<div class=\"opentfc-chart-canvas\" style=\"width:100%;height:" + escape(height) + ";\"></div>"
                + "<div class=\"opentfc-chart-source\" style=\"display:none;\">" + renderedBody + "</div>";
        return container("opentfc-chart", macroId, config, inner);
    }
}
