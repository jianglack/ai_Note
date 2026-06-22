package com.ainote.app.model;

import jakarta.validation.constraints.Size;

public class PlanModifyStepRequest {

    @Size(max = 200000, message = "Params payload is too large")
    private String params;

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }
}
