package org.opentfc.diag.lite;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

/**
 * log4j2 appender forwarding host WARN/ERROR entries into {@link LiteLogStore}.
 * Referenced only from the guarded installer, so a missing log4j-core wiring
 * degrades to "capture unavailable" without breaking the plugin.
 */
final class LiteRingAppender extends AbstractAppender {

    LiteRingAppender() {
        super("OpenTfcDiagLiteCapture", null, null, true);
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
        LiteLogStore.record(event.getLevel().name(), logger != null ? logger : "host", message, event.getThrown());
    }
}
