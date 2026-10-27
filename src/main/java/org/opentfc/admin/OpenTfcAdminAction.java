package org.opentfc.admin;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.xwork.RequireSecurityToken;
import org.opentfc.service.OpenTfcSettings;

/**
 * Admin page (/admin/opentfc/view-opentfc-admin.action): SQL limits and URL whitelist.
 * Lives under /admin/ so Confluence enforces administrator permission.
 */
public class OpenTfcAdminAction extends ConfluenceActionSupport {

    private transient OpenTfcSettings settings;

    private int sqlTimeoutSeconds = OpenTfcSettings.DEFAULT_SQL_TIMEOUT;
    private int maxResultRows = OpenTfcSettings.DEFAULT_MAX_ROWS;
    private String allowedUrlWhitelist = "";
    private boolean saved;

    public OpenTfcAdminAction() {
        try {
            PluginSettingsFactory psf = ComponentLocator.getComponent(PluginSettingsFactory.class);
            if (psf != null) {
                this.settings = new OpenTfcSettings(psf);
            }
        } catch (Throwable ignored) {
            // fallback in unit tests
        }
    }

    public OpenTfcAdminAction(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        setPluginSettingsFactory(pluginSettingsFactory);
    }

    public void setPluginSettingsFactory(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        if (pluginSettingsFactory != null) {
            this.settings = new OpenTfcSettings(pluginSettingsFactory);
        }
    }

    @Override
    public String execute() throws Exception {
        if (settings != null) {
            sqlTimeoutSeconds = settings.getSqlTimeoutSeconds();
            maxResultRows = settings.getMaxResultRows();
            allowedUrlWhitelist = settings.getUrlWhitelistRaw();
        }
        return SUCCESS;
    }

    /** Save settings (XSRF token required). */
    @RequireSecurityToken(true)
    public String save() throws Exception {
        if (settings != null) {
            settings.save(sqlTimeoutSeconds, maxResultRows, allowedUrlWhitelist);
            saved = true;
        }
        return execute();
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
