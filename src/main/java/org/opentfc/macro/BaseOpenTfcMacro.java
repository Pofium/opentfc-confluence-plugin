package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.confluence.util.HtmlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.opentfc.diag.OpenTfcLogStore;

/**
 * Base class for all OpenTFC macros: parameter helpers, body rendering, config serialization.
 */
public abstract class BaseOpenTfcMacro implements Macro {
    private static final Logger log = LoggerFactory.getLogger(BaseOpenTfcMacro.class);
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected final XhtmlContent xhtmlContent;

    protected BaseOpenTfcMacro() {
        this(null);
    }

    protected BaseOpenTfcMacro(XhtmlContent xhtmlContent) {
        this.xhtmlContent = xhtmlContent;
    }

    /** Return the stable macroId parameter, or generate one for legacy instances. */
    protected String getOrCreateMacroId(Map<String, String> parameters) {
        String macroId = parameters.get("macroId");
        if (macroId == null || macroId.trim().isEmpty()) {
            return "otfc-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return macroId;
    }

    /** True when rendering for PDF/Word/e-mail export, where JS is unavailable. */
    protected boolean isExportMode(ConversionContext ctx) {
        String outputType = ctx != null ? ctx.getOutputType() : null;
        return "pdf".equalsIgnoreCase(outputType)
                || "word".equalsIgnoreCase(outputType)
                || "email".equalsIgnoreCase(outputType);
    }

    /**
     * Convert rich-text macro body (storage format) into view HTML,
     * so nested macros (Jira Issues, Page Properties Report, etc.) get rendered.
     */
    protected String renderBody(String body, ConversionContext ctx) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        if (xhtmlContent == null) {
            return body;
        }
        try {
            return xhtmlContent.convertStorageToView(body, ctx);
        } catch (Exception e) {
            log.warn("OpenTFC: failed to render macro body, falling back to raw body: {}", e.getMessage());
            OpenTfcLogStore.warn("macro", "convertStorageToView failed, raw body fallback: " + e.getMessage());
            return body;
        }
    }

    /** Serialize macro config to JSON (for data-opentfc-config attribute). */
    protected String toJson(Object config) {
        try {
            return MAPPER.writeValueAsString(config);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static final Set<String> FIRST_USE_LOGGED = ConcurrentHashMap.newKeySet();

    /** Shows up on /plugins/servlet/opentfc/diagnostics; once per macro instance key. */
    private void logFirstUse(String macroId) {
        if (FIRST_USE_LOGGED.add(getClass().getSimpleName() + ":" + macroId)) {
            OpenTfcLogStore.info("macro", getClass().getSimpleName() + " rendered (macroId=" + macroId + ")");
        }
    }

    protected long pageIdOf(ConversionContext ctx) {
        return ctx != null && ctx.getEntity() != null ? ctx.getEntity().getId() : 0L;
    }

    protected String escape(String input) {
        return input != null ? HtmlUtil.htmlEncode(input) : "";
    }

    /** Wrap content into a standard OpenTFC container div. */
    protected String container(String cssClass, String macroId, Object config, String inner) {
        logFirstUse(macroId);
        return "<div class=\"opentfc-container " + cssClass + "\" id=\"" + escape(macroId)
                + "\" data-opentfc-config=\"" + escape(toJson(config)) + "\">" + inner + "</div>";
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
