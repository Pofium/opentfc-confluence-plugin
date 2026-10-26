package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CSV Table macro: data comes from a page attachment, external URL (whitelisted) or inline body.
 */
public class CsvTableMacro extends BaseOpenTfcMacro {

    public CsvTableMacro() {
        super(null);
    }

    public CsvTableMacro(@ComponentImport XhtmlContent xhtmlContent) {
        super(xhtmlContent);
    }

    @Override
    public BodyType getBodyType() {
        return BodyType.PLAIN_TEXT;
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext ctx)
            throws MacroExecutionException {
        String macroId = getOrCreateMacroId(parameters);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("macroId", macroId);
        config.put("pageId", pageIdOf(ctx));
        config.put("type", "csv");
        config.put("sourceType", parameters.getOrDefault("sourceType", "attachment"));
        config.put("attachmentName", parameters.getOrDefault("attachmentName", ""));
        config.put("url", parameters.getOrDefault("url", ""));
        config.put("delimiter", parameters.getOrDefault("delimiter", "auto"));
        config.put("encoding", parameters.getOrDefault("encoding", "UTF-8"));
        config.put("inline", body != null ? body : "");

        return container("opentfc-datasource opentfc-csv", macroId, config,
                "<div class=\"opentfc-datasource-grid\">Загрузка данных…</div>");
    }
}
