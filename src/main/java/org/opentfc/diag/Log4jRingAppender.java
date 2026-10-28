package org.opentfc.diag;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

/**
 * log4j2 appender forwarding host WARN/ERROR entries into {@link OpenTfcLogStore}.
 * Referenced only from {@link HostCapture#tryInstall()}, which guards the class load:
 * if log4j-core is not wired into the plugin, the rest of the plugin still works.
 */
final class Log4jRingAppender extends AbstractAppender {

    Log4jRingAppender() {
        super("OpenTfcHostCapture", null, null, true);
    }

    @Override
    public void append(LogEvent event) {
        if (event == null) {
            return;
        }
        String message = event.getMessage() != null ? event.getMessage().getFormattedMessage() : "";
        String logger = event.getLoggerName();
        if (logger != null && logger.length() > 60) {
            logger = logger.substring(logger.length() - 60);
        }
        OpenTfcLogStore.record(event.getLevel().name(),
                logger != null ? logger : "host",
                message,
                event.getThrown());
    }
}
