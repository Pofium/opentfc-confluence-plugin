package org.opentfc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a single row of cells in a table.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RowModel implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("cells")
    private List<CellModel> cells = new ArrayList<>();

    public RowModel() {}

    public RowModel(List<CellModel> cells) {
        this.cells = cells;
    }

    public List<CellModel> getCells() {
        return cells;
    }

    public void setCells(List<CellModel> cells) {
        this.cells = cells;
    }

    public void addCell(CellModel cell) {
        this.cells.add(cell);
    }
}
