package com.ainote.app.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aiChatRequestRequiresMessageOrQuery() {
        AiChatRequest request = new AiChatRequest();
        request.setScope("all");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("promptPresent");
    }

    @Test
    void aiNoteRequestRequiresNoteId() {
        AiNoteRequest request = new AiNoteRequest();
        request.setNoteId(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("noteId");
    }

    @Test
    void folderRequestRejectsBlankName() {
        FolderRequest request = new FolderRequest();
        request.setName(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    @Test
    void canvasRequestRejectsBlankSuppliedTitle() {
        CanvasRequest request = new CanvasRequest();
        request.setTitle(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("validTitle");
    }

    @Test
    void workflowRequestRejectsBlankSuppliedName() {
        WorkflowRequest request = new WorkflowRequest();
        request.setName(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("validName");
    }
}
