package com.ainote.app.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class GenerateCanvasRequest {

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 100, message = "Limit must be at most 100")
    private Integer limit;

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
