package com.ainote.app.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EvalDatasetRequest {

    @NotBlank(message = "Dataset name is required")
    @Size(max = 200, message = "Dataset name must be at most 200 characters")
    private String name;

    @Size(max = 1000, message = "Dataset description must be at most 1000 characters")
    private String description;

    @Size(max = 50, message = "Dataset type must be at most 50 characters")
    private String datasetType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDatasetType() {
        return datasetType;
    }

    public void setDatasetType(String datasetType) {
        this.datasetType = datasetType;
    }
}
