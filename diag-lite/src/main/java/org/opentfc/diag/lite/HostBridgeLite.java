package org.opentfc.diag.lite;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.plugin.PluginState;
import com.atlassian.plugin.event.events.PluginDisabledEvent;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.user.UserManager;

import java.util.ArrayList;
import java.util.List;

/**
 * All host API access of Diag Lite happens here, guarded: a missing/unexported host
 * class only fails the individual call (reported on the page), never the servlet.
 * The servlet itself compiles against nothing but servlet-api and this package.
 */
final class HostBridgeLite {

    static final String OPEN_TFC_KEY = "org.opentfc.confluence.plugin";

    private HostBridgeLite() {
    }

    /** "username", "" (SAL недоступен — degraded), null (аноним). */
    static String currentUsername() {
        try {
            UserManager um = ComponentLocator.getComponent(UserManager.class);
            if (um == null) {
                return "";
            }
            String name = um.getRemoteUsername();
            return name == null ? null : name;
        } catch (Throwable e) {
            return "";
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
                    : "ЕСТЬ, состояние " + p.getPluginState() + " (версия " + version + ") — причина в логах ниже";
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
            // reported via empty list + status
        }
        return out;
    }

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
