package org.opentfc.service;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Global OpenTFC admin settings stored in SAL PluginSettings (shared DB storage, cluster-safe).
 */
public class OpenTfcSettings {
    private static final String PREFIX = "org.opentfc.settings.";
    public static final int DEFAULT_SQL_TIMEOUT = 10;
    public static final int DEFAULT_MAX_ROWS = 50000;

    private final PluginSettingsFactory factory;

    public OpenTfcSettings(PluginSettingsFactory factory) {
        this.factory = factory;
    }

    private PluginSettings settings() {
        return factory.createGlobalSettings();
    }

    private int getInt(String key, int def, int min, int max) {
        Object raw = settings().get(PREFIX + key);
        if (raw == null) {
            return def;
        }
        try {
            int v = Integer.parseInt(raw.toString().trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int getSqlTimeoutSeconds() {
        return getInt("sqlTimeoutSeconds", DEFAULT_SQL_TIMEOUT, 1, 120);
    }

    public int getMaxResultRows() {
        return getInt("maxResultRows", DEFAULT_MAX_ROWS, 100, 500000);
    }

    public String getUrlWhitelistRaw() {
        Object raw = settings().get(PREFIX + "allowedUrlWhitelist");
        return raw == null ? "" : raw.toString();
    }

    public List<String> getUrlWhitelist() {
        return Arrays.stream(getUrlWhitelistRaw().split("[\\r\\n,;\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    /** Persist validated settings. */
    public void save(int sqlTimeoutSeconds, int maxResultRows, String whitelist) {
        PluginSettings s = settings();
        s.put(PREFIX + "sqlTimeoutSeconds", String.valueOf(Math.max(1, Math.min(120, sqlTimeoutSeconds))));
        s.put(PREFIX + "maxResultRows", String.valueOf(Math.max(100, Math.min(500000, maxResultRows))));
        s.put(PREFIX + "allowedUrlWhitelist", whitelist == null ? "" : whitelist.trim());
    }
}
