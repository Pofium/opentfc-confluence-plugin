package org.opentfc.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Active Objects entity for persisting individual user UI preferences.
 */
@Table("OPENTFC_USER_SET")
@Preload
public interface UserSettings extends Entity {

    @NotNull
    String getUserKey();
    void setUserKey(String userKey);

    @StringLength(StringLength.UNLIMITED)
    String getSettingsJson();
    void setSettingsJson(String settingsJson);

    long getUpdatedTime();
    void setUpdatedTime(long updatedTime);
}
