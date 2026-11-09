package org.opentfc.diag.lite;

import com.atlassian.event.api.EventListener;
import com.atlassian.plugin.event.events.PluginDisabledEvent;
import com.atlassian.plugin.event.events.PluginEnabledEvent;

/**
 * Public named class for plugin framework event listener.
 * Must be a named public class (not anonymous) so the Atlassian event dispatcher
 * can access its @EventListener methods via reflection across classloader boundaries.
 */
public class PluginFrameworkListener {

    @EventListener
    public void onEnabled(PluginEnabledEvent event) {
        LiteLogStore.info("plugin-framework", "ENABLED  " + key(event));
    }

    @EventListener
    public void onDisabled(PluginDisabledEvent event) {
        LiteLogStore.warn("plugin-framework", "DISABLED " + key(event));
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
