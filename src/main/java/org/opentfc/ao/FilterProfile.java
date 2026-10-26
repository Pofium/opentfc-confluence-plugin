package org.opentfc.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Active Objects entity for storing per-user table filter profiles.
 */
@Table("OPENTFC_PROFILE")
@Preload
public interface FilterProfile extends Entity {

    @NotNull
    long getPageId();
    void setPageId(long pageId);

    @NotNull
    String getMacroId();
    void setMacroId(String macroId);

    @NotNull
    String getUserKey();
    void setUserKey(String userKey);

    @NotNull
    String getProfileName();
    void setProfileName(String profileName);

    @StringLength(StringLength.UNLIMITED)
    String getFilterStateJson();
    void setFilterStateJson(String filterStateJson);

    long getCreatedTime();
    void setCreatedTime(long createdTime);

    long getUpdatedTime();
    void setUpdatedTime(long updatedTime);
}
