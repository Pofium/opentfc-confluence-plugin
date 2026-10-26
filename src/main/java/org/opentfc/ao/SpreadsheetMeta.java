package org.opentfc.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;

/**
 * Active Objects entity for tracking spreadsheet attachments, locking and versioning.
 */
@Table("OPENTFC_SHEET_META")
@Preload
public interface SpreadsheetMeta extends Entity {

    @NotNull
    long getPageId();
    void setPageId(long pageId);

    @NotNull
    String getMacroId();
    void setMacroId(String macroId);

    @NotNull
    String getAttachmentName();
    void setAttachmentName(String attachmentName);

    long getAttachmentId();
    void setAttachmentId(long attachmentId);

    int getVersion();
    void setVersion(int version);

    String getLockedByUser();
    void setLockedByUser(String lockedByUser);

    long getLockHeartbeatTime();
    void setLockHeartbeatTime(long lockHeartbeatTime);
}
