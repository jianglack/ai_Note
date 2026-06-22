package com.ainote.app.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public class CanvasRequest {

    @Size(max = 200, message = "Canvas title must be at most 200 characters")
    private String title;

    @Size(max = 200000, message = "Canvas data is too large")
    private String data;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @AssertTrue(message = "Canvas title must not be blank when supplied")
    public boolean isValidTitle() {
        return title == null || !title.isBlank();
    }

    @AssertTrue(message = "Canvas data must not be blank when supplied")
    public boolean isValidData() {
        return data == null || !data.isBlank();
    }
}
