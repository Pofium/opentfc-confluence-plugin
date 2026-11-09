package org.opentfc.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.confluence.util.HtmlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
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

    /**
     * Stable macro identifier.
     *
     * A random UUID per render used to break everything that is keyed by the identifier:
     * shared links (?tf_&lt;macroId&gt;=...), saved views and spreadsheet attachments, because the id
     * changed on every page view. Confluence does not expose the storage {@code ac:macro-id} to
     * macros, so stability is achieved in two steps:
     * <ol>
     *   <li>the explicit {@code macroId} parameter always wins — that is what authors should set
     *       whenever another macro has to reference this one;</li>
     *   <li>otherwise the identifier is a hash of the macro identity on the page: page id, macro
     *       name, rendered body and the non-cosmetic parameters (see {@code ignoredParams}).</li>
     * </ol>
     * Editing the table therefore rotates the id; that is deliberate — it is the only content we
     * can key on — and an explicit {@code macroId} avoids it.
     */
    protected String getOrCreateMacroId(Map<String, String> parameters, String macroName, String body,
                                        ConversionContext ctx, String... ignoredParams) {
        String macroId = parameters.get("macroId");
        if (macroId != null && !macroId.trim().isEmpty()) {
            return macroId.trim();
        }

        Set<String> ignored = Set.of(ignoredParams);
        StringBuilder seed = new StringBuilder()
                .append(pageIdOf(ctx)).append('|')
                .append(macroName).append('|');
        if (body != null) {
            seed.append(body.replaceAll("\\s+", "").trim()).append('|');
        }
        parameters.entrySet().stream()
                .filter(e -> !ignored.contains(e.getKey()) && !"macroId".equals(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> seed.append(e.getKey()).append('=').append(e.getValue()).append(';'));

        return "otfc-" + sha256Hex(seed.toString()).substring(0, 8);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is required by every JDK; hashCode() is only a defensive last resort.
            return Integer.toHexString(value.hashCode());
        }
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
