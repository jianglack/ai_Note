package com.ainote.app.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EvalRunRequest {

    @NotBlank(message = "Dataset id is required")
    @Size(max = 64, message = "Dataset id must be at most 64 characters")
    private String datasetId;

    @Size(max = 50, message = "Run type must be at most 50 characters")
    private String runType;

    @Size(max = 200000, message = "Config payload is too large")
    private String config;

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getRunType() {
        return runType;
    }

    public void setRunType(String runType) {
        this.runType = runType;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }
}
