package com.ainote.app.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class NoteDatabaseRowRequest {

    @Size(max = 200000, message = "Row data payload is too large")
    private String data;

    @Min(value = 0, message = "Sort order must be non-negative")
    private Integer sortOrder;

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
