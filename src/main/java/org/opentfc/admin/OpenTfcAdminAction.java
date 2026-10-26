package org.opentfc.admin;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.xwork.RequireSecurityToken;
import org.opentfc.service.OpenTfcSettings;

/**
 * Admin page (/admin/opentfc/view-opentfc-admin.action): SQL limits and URL whitelist.
 * Lives under /admin/ so Confluence enforces administrator permission.
 */
public class OpenTfcAdminAction extends ConfluenceActionSupport {

    private final transient OpenTfcSettings settings;

    private int sqlTimeoutSeconds = OpenTfcSettings.DEFAULT_SQL_TIMEOUT;
    private int maxResultRows = OpenTfcSettings.DEFAULT_MAX_ROWS;
    private String allowedUrlWhitelist = "";
    private boolean saved;

    public OpenTfcAdminAction(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.settings = new OpenTfcSettings(pluginSettingsFactory);
    }

    @Override
    public String execute() throws Exception {
        sqlTimeoutSeconds = settings.getSqlTimeoutSeconds();
        maxResultRows = settings.getMaxResultRows();
        allowedUrlWhitelist = settings.getUrlWhitelistRaw();
        return SUCCESS;
    }

    /** Save settings (XSRF token required). */
    @RequireSecurityToken(true)
    public String save() throws Exception {
        settings.save(sqlTimeoutSeconds, maxResultRows, allowedUrlWhitelist);
        saved = true;
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
