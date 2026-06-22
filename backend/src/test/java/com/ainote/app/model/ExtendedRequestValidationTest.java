package com.ainote.app.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtendedRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void mindMapRequestRejectsBlankSuppliedTitle() {
        MindMapRequest request = new MindMapRequest();
        request.setTitle(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("validTitle");
    }

    @Test
    void noteDatabaseRequestRequiresNoteIdOnCreate() {
        NoteDatabaseRequest request = new NoteDatabaseRequest();
        request.setName("Research");

        assertThat(validator.validate(request, NoteDatabaseRequest.Create.class))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("noteId");
    }

    @Test
    void noteDatabaseRowRequestRejectsNegativeSortOrder() {
        NoteDatabaseRowRequest request = new NoteDatabaseRowRequest();
        request.setSortOrder(-1);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("sortOrder");
    }

    @Test
    void typedLinkRequestRequiresSourceAndTargetNotes() {
        TypedLinkRequest request = new TypedLinkRequest();
        request.setSourceNoteId("source");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("targetNoteId");
    }

    @Test
    void evalDatasetRequestRejectsBlankName() {
        EvalDatasetRequest request = new EvalDatasetRequest();
        request.setName(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    @Test
    void evalItemRequestRejectsBlankQuestion() {
        EvalItemRequest request = new EvalItemRequest();
        request.setQuestion(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("question");
    }

    @Test
    void evalRunRequestRequiresDatasetId() {
        EvalRunRequest request = new EvalRunRequest();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("datasetId");
    }

    @Test
    void taskScheduleRequestRejectsInvalidTriggerTypeAndRunCount() {
        TaskScheduleRequest request = new TaskScheduleRequest();
        request.setQuery("Run report");
        request.setTriggerType("SOMETIMES");
        request.setMaxRunCount(0);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("triggerType", "maxRunCount");
    }

    @Test
    void adminRagEvaluationRequestRequiresCases() {
        AdminRagEvaluationRequest request = new AdminRagEvaluationRequest();
        request.setK(5);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("cases");
    }

    @Test
    void chatSaveRequestRequiresAtLeastOneMessage() {
        ChatSaveRequest request = new ChatSaveRequest();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("messagePresent");
    }

    @Test
    void actionFeedbackRequestRequiresActionJsonAndConfirmedDecision() {
        ActionFeedbackRequest request = new ActionFeedbackRequest();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("actionJson", "confirmed");
    }

    @Test
    void mediaTableRequestRequiresNoteIdAndTablePayload() {
        MediaTableRequest request = new MediaTableRequest();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("noteId", "tablePayloadPresent");
    }

    @Test
    void planSmartChatRequestRequiresQuery() {
        PlanSmartChatRequest request = new PlanSmartChatRequest();
        request.setQuery(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("query");
    }

    @Test
    void planModifyStepRequestLimitsParamsSize() {
        PlanModifyStepRequest request = new PlanModifyStepRequest();
        request.setParams("x".repeat(200001));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("params");
    }
}
