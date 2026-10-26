package org.opentfc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified representation of a parsed table structure.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TableModel implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("id")
    private String id;

    @JsonProperty("columns")
    private List<ColumnModel> columns = new ArrayList<>();

    @JsonProperty("rows")
    private List<RowModel> rows = new ArrayList<>();

    @JsonProperty("totalRows")
    private int totalRows;

    public TableModel() {}

    public TableModel(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<ColumnModel> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnModel> columns) {
        this.columns = columns;
    }

    public void addColumn(ColumnModel column) {
        this.columns.add(column);
    }

    public List<RowModel> getRows() {
        return rows;
    }

    public void setRows(List<RowModel> rows) {
        this.rows = rows;
        this.totalRows = rows != null ? rows.size() : 0;
    }

    public void addRow(RowModel row) {
        this.rows.add(row);
        this.totalRows = this.rows.size();
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }
}
