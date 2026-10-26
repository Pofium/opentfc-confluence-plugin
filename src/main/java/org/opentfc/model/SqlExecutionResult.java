package org.opentfc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * Result of executing SQL via DuckDB engine.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SqlExecutionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("table")
    private TableModel table;

    @JsonProperty("executionTimeMs")
    private long executionTimeMs;

    @JsonProperty("errorMessage")
    private String errorMessage;

    public SqlExecutionResult() {}

    public static SqlExecutionResult success(TableModel table, long executionTimeMs) {
        SqlExecutionResult res = new SqlExecutionResult();
        res.success = true;
        res.table = table;
        res.executionTimeMs = executionTimeMs;
        return res;
    }

    public static SqlExecutionResult error(String errorMessage, long executionTimeMs) {
        SqlExecutionResult res = new SqlExecutionResult();
        res.success = false;
        res.errorMessage = errorMessage;
        res.executionTimeMs = executionTimeMs;
        return res;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public TableModel getTable() {
        return table;
    }

    public void setTable(TableModel table) {
        this.table = table;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
