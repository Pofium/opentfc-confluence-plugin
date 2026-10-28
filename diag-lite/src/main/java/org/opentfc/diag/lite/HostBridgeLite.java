package org.opentfc.diag.lite;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.plugin.PluginState;
import com.atlassian.plugin.event.events.PluginDisabledEvent;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.user.UserManager;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Все обращения к хост-API Diag Lite — здесь, под guard'ом: недоступный/неэкспортируемый
 * класс хоста роняет только конкретный вызов (виден на странице), но не плагин.
 *
 * Файлы журналов читаются ТОЛЬКО через перечисление фактического содержимого каталога
 * logs (имя запрошенного файла сверяется equals'ом со списком) — путь из пользовательского
 * ввода нигде не конструируется.
 */
final class HostBridgeLite {

    static final String OPEN_TFC_KEY = "org.opentfc.confluence.plugin";

    private static volatile String salError = "";
    private static volatile String homeSource = "none";

    /** Текст последней ошибки SAL (для строки статуса). */
    static String salErrorText() {
        return salError;
    }

    /** Откуда взят каталог журналов: "sal" | "sysprop" | "none". */
    static String homeSource() {
        return homeSource;
    }

    private HostBridgeLite() {
    }

    /** "username" (авторизован), null (аноним; в некоторых версиях SAL аноним — ""), "!sal" (SAL недоступен). */
    static String currentUsername() {
        try {
            UserManager um = ComponentLocator.getComponent(UserManager.class);
            if (um == null) {
                salError = "ComponentLocator вернул null для UserManager";
                return "!sal";
            }
            String name = um.getRemoteUsername();
            return (name == null || name.isEmpty()) ? null : name;
        } catch (Throwable e) {
            salError = e.getClass().getName() + ": " + e.getMessage();
            return "!sal";
        }
    }

    /** Однострочное состояние OpenTFC для статус-панели. */
    static String openTfcState() {
        try {
            PluginAccessor accessor = ComponentLocator.getComponent(PluginAccessor.class);
            if (accessor == null) {
                return "PluginAccessor недоступен";
            }
            Plugin p = accessor.getPlugin(OPEN_TFC_KEY);
            if (p == null) {
                return "не установлен (ключ " + OPEN_TFC_KEY + " не найден)";
            }
            String version = p.getPluginInformation().getVersion();
            return p.getPluginState() == PluginState.ENABLED
                    ? "ВКЛЮЧЁН, версия " + version
                    : "ЕСТЬ, состояние " + p.getPluginState() + " (версия " + version + ") — причина в журналах ниже";
        } catch (Throwable e) {
            return "ошибка: " + e;
        }
    }

    /** Ключи всех отключённых приложений (каскад DISABLED — сигнатура поломки). */
    static List<String> disabledPlugins() {
        List<String> out = new ArrayList<>();
        try {
            PluginAccessor accessor = ComponentLocator.getComponent(PluginAccessor.class);
            if (accessor == null) {
                return out;
            }
            for (Plugin p : accessor.getPlugins()) {
                if (p.getPluginState() != PluginState.ENABLED) {
                    out.add(p.getKey() + "  [" + p.getPluginInformation().getVersion() + "]");
                    if (out.size() >= 200) {
                        out.add("... (показаны первые 200)");
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // пустой список + статус
        }
        return out;
    }

    // ===== Файлы журналов =====

    static final class JournalEntry {
        final String name;
        final long sizeKb;
        final String modified;
        final String tail;

        JournalEntry(String name, long sizeKb, String modified, String tail) {
            this.name = name;
            this.sizeKb = sizeKb;
            this.modified = modified;
            this.tail = tail;
        }
    }

    private static File logsDir() {
        try {
            ApplicationProperties props = ComponentLocator.getComponent(ApplicationProperties.class);
            if (props != null) {
                File home = null;
                java.nio.file.Path local = props.getLocalHomeDirectory() != null
                        ? props.getLocalHomeDirectory().orElse(null) : null;
                if (local != null) {
                    home = local.toFile();
                } else if (props.getHomeDirectory() != null) {
                    home = props.getHomeDirectory();
                }
                if (home != null) {
                    File dir = new File(home, "logs");
                    if (dir.isDirectory()) {
                        homeSource = "sal";
                        return dir;
                    }
                }
            }
        } catch (Throwable e) {
            salError = e.getClass().getName() + ": " + e.getMessage();
        }
        Path probe = HomeDirProbe.logsDirFromSysProp();
        if (probe != null) {
            homeSource = "sysprop";
            return probe.toFile();
        }
        homeSource = "none";
        return null;
    }

    /** Фактическое содержимое каталога logs (новые сверху, максимум limit). */
    private static List<File> listLogs(int limit) {
        List<File> out = new ArrayList<>();
        try {
            File dir = logsDir();
            if (dir == null) {
                return out;
            }
            File[] files = dir.listFiles((d, n) -> n.matches("[A-Za-z0-9._ -]{1,80}\\.log(\\.[0-9]+)?"));
            if (files == null) {
                return out;
            }
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            for (File f : files) {
                out.add(f);
                if (out.size() >= limit) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            // пусто
        }
        return out;
    }

    /** Имя самого свежего журнала (для скачивания), или null. */
    static String mainJournalName() {
        List<File> files = listLogs(1);
        return files.isEmpty() ? null : files.get(0).getName();
    }

    /**
     * Хвосты журналов для страницы. Читаются только файлы из фактического листинга
     * каталога logs (fresh listing); пользовательский ввод в пути не участвует.
     */
    static List<JournalEntry> journalTails(int maxFiles, int tailBytesPerFile) {
        List<JournalEntry> out = new ArrayList<>();
        for (File f : listLogs(maxFiles)) {
            String tail = "не удалось прочитать";
            try {
                tail = readTail(f, tailBytesPerFile);
            } catch (Throwable e) {
                tail = "ошибка чтения: " + e;
            }
            out.add(new JournalEntry(f.getName(), f.length() / 1024,
                    new java.util.Date(f.lastModified()).toString(), tail));
        }
        return out;
    }

    /** Хвост самого свежего журнала (для скачивания), или null если журналов нет. */
    static String mainJournalTail(int maxBytes) {
        List<File> files = listLogs(1);
        if (files.isEmpty()) {
            return null;
        }
        try {
            return readTail(files.get(0), maxBytes);
        } catch (Throwable e) {
            return "ошибка чтения: " + e;
        }
    }

    private static String readTail(File f, int maxBytes) throws IOException {
        long len = f.length();
        long start = Math.max(0, len - maxBytes);
        byte[] buf = new byte[(int) (len - start)];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek(start);
            raf.readFully(buf);
        }
        String s = new String(buf, StandardCharsets.UTF_8);
        if (start > 0) {
            int nl = s.indexOf('\n');
            s = "(…обрезано начало…)\n" + (nl >= 0 ? s.substring(nl + 1) : s);
        }
        return s;
    }

    // ===== События plugin framework =====

    private static final Object EVENT_HOOK_LOCK = new Object();
    private static boolean eventsRegistered = false;

    /** Регистрация слушателя ENABLED/DISABLED событий plugin framework. */
    static String tryRegisterEvents() {
        synchronized (EVENT_HOOK_LOCK) {
            if (eventsRegistered) {
                return "registered";
            }
            try {
                EventPublisher publisher = ComponentLocator.getComponent(EventPublisher.class);
                if (publisher == null) {
                    return "EventPublisher недоступен";
                }
                publisher.register(new Object() {
                    @EventListener
                    public void onEnabled(PluginEnabledEvent event) {
                        LiteLogStore.info("plugin-framework", "ENABLED  " + key(event));
                    }

                    @EventListener
                    public void onDisabled(PluginDisabledEvent event) {
                        LiteLogStore.warn("plugin-framework", "DISABLED " + key(event));
                    }
                });
                eventsRegistered = true;
                LiteLogStore.info("diag-lite", "Plugin framework event listener registered");
                return "registered";
            } catch (Throwable e) {
                LiteLogStore.warn("diag-lite", "Event registration failed: " + e);
                return "ошибка: " + e;
            }
        }
    }

    private static String key(Object event) {
        try {
            if (event instanceof PluginEnabledEvent) {
                return ((PluginEnabledEvent) event).getPlugin().getKey();
            }
            if (event instanceof PluginDisabledEvent) {
                return ((PluginDisabledEvent) event).getPlugin().getKey();
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return "<unknown>";
    }
}
