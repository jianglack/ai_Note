package com.ainote.app.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public class WorkflowRequest {

    @Size(max = 200, message = "Workflow name must be at most 200 characters")
    private String name;

    @Size(max = 1000, message = "Workflow description must be at most 1000 characters")
    private String description;

    @Size(max = 50, message = "Trigger type must be at most 50 characters")
    private String triggerType;

    @Size(max = 20000, message = "Trigger config is too large")
    private String triggerConfig;

    @Size(max = 200000, message = "Workflow steps are too large")
    private String steps;

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

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggerConfig() {
        return triggerConfig;
    }

    public void setTriggerConfig(String triggerConfig) {
        this.triggerConfig = triggerConfig;
    }

    public String getSteps() {
        return steps;
    }

    public void setSteps(String steps) {
        this.steps = steps;
    }

    @AssertTrue(message = "Workflow name must not be blank when supplied")
    public boolean isValidName() {
        return name == null || !name.isBlank();
    }
}
