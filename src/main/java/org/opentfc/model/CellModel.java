package org.opentfc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * Model representing an individual table cell with raw and formatted representation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CellModel implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("val")
    private Object rawValue;

    @JsonProperty("html")
    private String html;

    @JsonProperty("type")
    private ColumnType type = ColumnType.TEXT;

    public CellModel() {}

    public CellModel(Object rawValue, String html, ColumnType type) {
        this.rawValue = rawValue;
        this.html = html;
        this.type = type;
    }

    public Object getRawValue() {
        return rawValue;
    }

    public void setRawValue(Object rawValue) {
        this.rawValue = rawValue;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public ColumnType getType() {
        return type;
    }

    public void setType(ColumnType type) {
        this.type = type;
    }
}
