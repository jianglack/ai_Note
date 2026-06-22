package com.ainote.app.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FolderRequest {

    @NotBlank(message = "Folder name is required")
    @Size(max = 100, message = "Folder name must be at most 100 characters")
    private String name;

    @Size(max = 64, message = "Parent folder id must be at most 64 characters")
    private String parentId;

    @Size(max = 20, message = "Folder color must be at most 20 characters")
    private String color;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
