package org.opentfc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing metadata and properties of a table column.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ColumnModel implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("index")
    private int index;

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private ColumnType type = ColumnType.TEXT;

    @JsonProperty("options")
    private List<String> options = new ArrayList<>();

    public ColumnModel() {}

    public ColumnModel(int index, String name, ColumnType type) {
        this.index = index;
        this.name = name;
        this.type = type;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ColumnType getType() {
        return type;
    }

    public void setType(ColumnType type) {
        this.type = type;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }
}
