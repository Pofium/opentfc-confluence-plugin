package org.opentfc.diag;

/**
 * Guarded entry point for host log capture. Never touches log4j-core classes directly,
 * so a missing/unexported log4j-core degrades to "capture unavailable" instead of
 * breaking the plugin.
 */
public final class HostCapture {

    private static volatile boolean attempted = false;

    private HostCapture() {
    }

    /** Idempotent. Returns a human-readable install status for the diagnostics page. */
    public static String tryInstall() {
        if (OpenTfcLogStore.isHostCaptureInstalled()) {
            return "installed";
        }
        attempted = true;
        try {
            Class.forName("org.apache.logging.log4j.core.LoggerContext");
            return HostLogCaptureInstaller.install();
        } catch (Throwable e) {
            OpenTfcLogStore.warn("diag", "Host log capture unavailable: " + e.getClass().getName()
                    + ": " + e.getMessage());
            return "unavailable (" + e.getClass().getSimpleName() + ")";
        }
    }

    public static boolean wasAttempted() {
        return attempted;
    }
}
