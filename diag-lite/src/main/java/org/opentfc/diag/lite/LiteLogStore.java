package org.opentfc.diag.lite;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory ring buffer of diagnostic entries for Diag Lite.
 * Dependency-free so it works regardless of what else is broken.
 */
public final class LiteLogStore {

    public static final int MAX_ENTRIES = 2000;
    private static final int MAX_STACK_LINES = 15;

    public static final class Entry {
        public final long timestamp;
        public final String level;
        public final String source;
        public final String message;

        Entry(long timestamp, String level, String source, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.source = source;
            this.message = message;
        }
    }

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static final AtomicLong DROPPED = new AtomicLong();
    private static volatile boolean hostCaptureInstalled = false;

    static {
        record("INFO", "diag-lite", "Diag Lite log store initialized", null);
    }

    private LiteLogStore() {
    }

    public static void info(String source, String message) {
        record("INFO", source, message, null);
    }

    public static void warn(String source, String message) {
        record("WARN", source, message, null);
    }

    public static void record(String level, String source, String message, Throwable t) {
        String msg = message == null ? "" : message;
        if (t != null) {
            msg = (msg.isEmpty() ? "" : msg + "\n") + firstStackLines(t);
        }
        synchronized (ENTRIES) {
            if (ENTRIES.size() >= MAX_ENTRIES) {
                ENTRIES.pollFirst();
                DROPPED.incrementAndGet();
            }
            ENTRIES.addLast(new Entry(System.currentTimeMillis(), level, source, msg));
        }
    }

    public static List<Entry> snapshot() {
        synchronized (ENTRIES) {
            return new ArrayList<>(ENTRIES);
        }
    }

    public static long droppedCount() {
        return DROPPED.get();
    }

    public static boolean isHostCaptureInstalled() {
        return hostCaptureInstalled;
    }

    static void setHostCaptureInstalled(boolean installed) {
        hostCaptureInstalled = installed;
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
            DROPPED.set(0);
        }
        record("INFO", "diag-lite", "Log buffer cleared", null);
    }

    public static String asText() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : snapshot()) {
            sb.append(Instant.ofEpochMilli(e.timestamp)).append(' ')
              .append(String.format("%-5s", e.level)).append('[').append(e.source).append("] ")
              .append(e.message.replace("\n", "\n    "))
              .append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static String firstStackLines(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String[] lines = sw.toString().split("\r?\n", MAX_STACK_LINES + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, MAX_STACK_LINES); i++) {
            sb.append(lines[i]).append('\n');
        }
        if (lines.length > MAX_STACK_LINES) {
            sb.append("    ... (").append(lines.length - MAX_STACK_LINES).append(" more lines)");
        }
        return sb.toString().trim();
    }
}
