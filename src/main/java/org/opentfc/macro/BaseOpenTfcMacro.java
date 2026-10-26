package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import org.apache.commons.lang3.StringEscapeUtils;

import java.util.Map;
import java.util.UUID;

/**
 * Base class for all OpenTFC macros with common parameter extraction and rendering helpers.
 */
public abstract class BaseOpenTfcMacro implements Macro {

    protected String getOrCreateMacroId(Map<String, String> parameters) {
        String macroId = parameters.get("macroId");
        if (macroId == null || macroId.trim().isEmpty()) {
            macroId = "otfc-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return macroId;
    }

    protected boolean isExportMode(ConversionContext conversionContext) {
        String outputType = conversionContext.getOutputType();
        return "pdf".equalsIgnoreCase(outputType) ||
               "word".equalsIgnoreCase(outputType) ||
               "email".equalsIgnoreCase(outputType);
    }

    protected String escape(String input) {
        return input != null ? StringEscapeUtils.escapeHtml4(input) : "";
    }

    @Override
    public BodyType getBodyType() {
        return BodyType.RICH_TEXT;
    }

    @Override
    public OutputType getOutputType() {
        return OutputType.BLOCK;
    }
}
