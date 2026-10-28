package org.opentfc.diag.lite;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

/**
 * Performs the actual log4j2 wiring. Isolated in its own class so a NoClassDefFoundError
 * only affects this class, not the plugin.
 */
final class LiteCaptureInstaller {

    private LiteCaptureInstaller() {
    }

    static String install() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        if (config.getAppender("OpenTfcDiagLiteCapture") != null) {
            LiteLogStore.setHostCaptureInstalled(true);
            return "installed";
        }
        LiteRingAppender appender = new LiteRingAppender();
        appender.start();
        config.addAppender(appender);
        config.getRootLogger().addAppender(appender, Level.WARN, null);
        ctx.updateLoggers();
        LiteLogStore.setHostCaptureInstalled(true);
        LiteLogStore.info("diag-lite", "Host log capture installed: WARN/ERROR entries of ALL plugins "
                + "are now visible on /plugins/servlet/diag-lite");
        return "installed";
    }
}
