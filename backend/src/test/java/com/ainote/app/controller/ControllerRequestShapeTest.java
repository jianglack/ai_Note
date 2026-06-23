package com.ainote.app.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerRequestShapeTest {

    @Test
    void coreControllersUseValidatedRequestDtosInsteadOfRawMaps() throws Exception {
        assertValidatedDto("FolderController.java", "FolderRequest");
        assertValidatedDto("CanvasController.java", "CanvasRequest");
        assertValidatedDto("WorkflowController.java", "WorkflowRequest");
        assertValidatedDto("MindMapController.java", "MindMapRequest");
        assertValidatedDto("NoteDatabaseController.java", "NoteDatabaseRequest");
        assertValidatedDto("NoteDatabaseController.java", "NoteDatabaseRowRequest");
        assertValidatedDto("TypedLinkController.java", "TypedLinkRequest");
        assertValidatedDto("EvalController.java", "EvalDatasetRequest");
        assertValidatedDto("EvalController.java", "EvalItemRequest");
        assertValidatedDto("EvalController.java", "EvalRunRequest");
        assertValidatedDto("TaskScheduleController.java", "TaskScheduleRequest");
        assertValidatedDto("AdminController.java", "AdminRagEvaluationRequest");
        assertValidatedDto("AdminController.java", "AdminAgentEvaluationRequest");
        assertValidatedDto("AiController.java", "ChatSaveRequest");
        assertValidatedDto("AiController.java", "ActionFeedbackRequest");
        assertValidatedDto("AiController.java", "RagFeedbackRequest");
        assertValidatedDto("AiController.java", "GenerateCanvasRequest");
        assertValidatedDto("MediaController.java", "MediaTableRequest");
        assertValidatedDto("AnnotationController.java", "CreateAnnotationRequest");
        assertValidatedDto("AnnotationController.java", "UpdateAnnotationRequest");
        assertValidatedDto("PlanController.java", "PlanSmartChatRequest");
        assertValidatedDto("PlanController.java", "PlanRouteRequest");
        assertValidatedDto("PlanController.java", "PlanModifyStepRequest");
    }

    private static void assertValidatedDto(String fileName, String dtoName) throws Exception {
        Path source = Path.of("src", "main", "java", "com", "ainote", "app", "controller", fileName);
        String content = Files.readString(source);

        assertThat(content).contains(dtoName);
        assertThat(content).containsAnyOf(
                "@Valid @RequestBody " + dtoName,
                "@Valid @RequestBody(required = false) " + dtoName,
                "@Validated(");
        assertThat(content).doesNotContain("@RequestBody Map<String, String>");
        assertThat(content).doesNotContain("@RequestBody Map<String, Object>");
    }
}
