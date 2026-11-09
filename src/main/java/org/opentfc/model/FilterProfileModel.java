package org.opentfc.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * DTO for filter profile payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FilterProfileModel implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("id")
    private long id;

    @JsonProperty("pageId")
    private long pageId;

    @JsonProperty("macroId")
    private String macroId;

    @JsonProperty("name")
    private String name;

    /**
     * Wire name matches the getter-derived one ("stateJson"), so the payload is identical
     * whether it is produced by the plugin's Jackson or by the Confluence platform Jackson.
     * "state" stays accepted as a legacy alias.
     */
    @JsonProperty("stateJson")
    @JsonAlias("state")
    private String stateJson;

    public FilterProfileModel() {}

    public FilterProfileModel(long id, long pageId, String macroId, String name, String stateJson) {
        this.id = id;
        this.pageId = pageId;
        this.macroId = macroId;
        this.name = name;
        this.stateJson = stateJson;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPageId() {
        return pageId;
    }

    public void setPageId(long pageId) {
        this.pageId = pageId;
    }

    public String getMacroId() {
        return macroId;
    }

    public void setMacroId(String macroId) {
        this.macroId = macroId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStateJson() {
        return stateJson;
    }

    public void setStateJson(String stateJson) {
        this.stateJson = stateJson;
    }
}
