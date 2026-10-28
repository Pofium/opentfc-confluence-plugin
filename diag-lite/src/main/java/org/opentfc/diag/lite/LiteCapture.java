package org.opentfc.diag.lite;

/**
 * Guarded installer for the host log capture. Log4j-core classes are referenced only
 * from {@link LiteCaptureInstaller}, which this class loads behind a Class.forName check.
 */
final class LiteCapture {

    private LiteCapture() {
    }

    /** Idempotent. Returns human-readable status for the page. */
    static String tryInstall() {
        if (LiteLogStore.isHostCaptureInstalled()) {
            return "installed";
        }
        try {
            Class.forName("org.apache.logging.log4j.core.LoggerContext");
            return LiteCaptureInstaller.install();
        } catch (Throwable e) {
            LiteLogStore.warn("diag-lite", "Host log capture unavailable: " + e);
            return "unavailable (" + e.getClass().getSimpleName() + ")";
        }
    }
}
