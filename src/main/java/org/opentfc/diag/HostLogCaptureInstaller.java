package org.opentfc.diag;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

/**
 * Performs the actual log4j2 wiring. Isolated in its own class so that
 * {@code NoClassDefFoundError} only affects this class, not the whole plugin.
 */
final class HostLogCaptureInstaller {

    private HostLogCaptureInstaller() {
    }

    static String install() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        if (config.getAppender("OpenTfcHostCapture") != null) {
            OpenTfcLogStore.setHostCaptureInstalled(true);
            return "installed";
        }
        Log4jRingAppender appender = new Log4jRingAppender();
        appender.start();
        config.addAppender(appender);
        config.getRootLogger().addAppender(appender, Level.WARN, null);
        ctx.updateLoggers();
        OpenTfcLogStore.setHostCaptureInstalled(true);
        OpenTfcLogStore.info("diag", "Host log capture installed: WARN/ERROR entries of ALL plugins "
                + "are now visible on the diagnostics page");
        return "installed";
    }
}
