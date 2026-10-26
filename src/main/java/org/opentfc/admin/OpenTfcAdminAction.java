package org.opentfc.admin;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

/**
 * Confluence Administration Action for managing OpenTFC global settings.
 */
public class OpenTfcAdminAction extends ConfluenceActionSupport {

    private static final String SETTINGS_KEY = "org.opentfc.settings.";
    private final PluginSettingsFactory pluginSettingsFactory;

    private int sqlTimeoutSeconds = 10;
    private int maxResultRows = 50000;
    private String allowedUrlWhitelist = "";
    private boolean saved = false;

    public OpenTfcAdminAction(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public String execute() throws Exception {
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();

        String timeoutStr = (String) settings.get(SETTINGS_KEY + "sqlTimeoutSeconds");
        if (timeoutStr != null) {
            try {
                this.sqlTimeoutSeconds = Integer.parseInt(timeoutStr);
            } catch (NumberFormatException ignored) {}
        }

        String maxRowsStr = (String) settings.get(SETTINGS_KEY + "maxResultRows");
        if (maxRowsStr != null) {
            try {
                this.maxResultRows = Integer.parseInt(maxRowsStr);
            } catch (NumberFormatException ignored) {}
        }

        this.allowedUrlWhitelist = (String) settings.get(SETTINGS_KEY + "allowedUrlWhitelist");
        if (this.allowedUrlWhitelist == null) {
            this.allowedUrlWhitelist = "";
        }

        return SUCCESS;
    }

    public String save() {
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        settings.put(SETTINGS_KEY + "sqlTimeoutSeconds", String.valueOf(sqlTimeoutSeconds));
        settings.put(SETTINGS_KEY + "maxResultRows", String.valueOf(maxResultRows));
        settings.put(SETTINGS_KEY + "allowedUrlWhitelist", allowedUrlWhitelist != null ? allowedUrlWhitelist : "");
        this.saved = true;
        return SUCCESS;
    }

    public int getSqlTimeoutSeconds() {
        return sqlTimeoutSeconds;
    }

    public void setSqlTimeoutSeconds(int sqlTimeoutSeconds) {
        this.sqlTimeoutSeconds = sqlTimeoutSeconds;
    }

    public int getMaxResultRows() {
        return maxResultRows;
    }

    public void setMaxResultRows(int maxResultRows) {
        this.maxResultRows = maxResultRows;
    }

    public String getAllowedUrlWhitelist() {
        return allowedUrlWhitelist;
    }

    public void setAllowedUrlWhitelist(String allowedUrlWhitelist) {
        this.allowedUrlWhitelist = allowedUrlWhitelist;
    }

    public boolean isSaved() {
        return saved;
    }
}
