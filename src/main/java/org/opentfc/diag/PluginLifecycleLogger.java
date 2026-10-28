package org.opentfc.diag;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.event.events.PluginDisabledEvent;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.plugin.event.events.PluginFrameworkStartedEvent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.inject.Named;

/**
 * Logs plugin framework ENABLED/DISABLED events into the browser-visible store.
 * A cascade of DISABLED events after installing an app is exactly the signature of
 * the "raw Velocity everywhere" breakage, so it must be visible without server access.
 */
@Named
public class PluginLifecycleLogger implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(PluginLifecycleLogger.class);

    private final EventPublisher eventPublisher;

    public PluginLifecycleLogger() {
        this(null);
    }

    public PluginLifecycleLogger(@ComponentImport EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void afterPropertiesSet() {
        OpenTfcLogStore.setLifecycleUp(true);
        OpenTfcLogStore.info("lifecycle", "Spring context started; plugin framework event listener registered");
        HostCapture.tryInstall();
        if (eventPublisher == null) {
            OpenTfcLogStore.warn("lifecycle", "EventPublisher is not wired: plugin framework events will not be captured");
            return;
        }
        try {
            eventPublisher.register(this);
        } catch (Throwable e) {
            OpenTfcLogStore.error("lifecycle", "Failed to register event listener", e);
        }
    }

    @Override
    public void destroy() {
        if (eventPublisher != null) {
            try {
                eventPublisher.unregister(this);
            } catch (Throwable ignored) {
                // plugin is going away anyway
            }
        }
    }

    @EventListener
    public void onPluginEnabled(PluginEnabledEvent event) {
        OpenTfcLogStore.info("plugin-framework", "ENABLED  " + pluginKey(event));
    }

    @EventListener
    public void onPluginDisabled(PluginDisabledEvent event) {
        OpenTfcLogStore.warn("plugin-framework", "DISABLED " + pluginKey(event));
    }

    @EventListener
    public void onFrameworkStarted(PluginFrameworkStartedEvent event) {
        OpenTfcLogStore.info("plugin-framework", "Plugin framework started");
    }

    private static String pluginKey(Object event) {
        try {
            if (event instanceof PluginEnabledEvent) {
                return ((PluginEnabledEvent) event).getPlugin().getKey();
            }
            if (event instanceof PluginDisabledEvent) {
                return ((PluginDisabledEvent) event).getPlugin().getKey();
            }
        } catch (Throwable t) {
            log.debug("OpenTFC: cannot read plugin key from event", t);
        }
        return "<unknown>";
    }
}
