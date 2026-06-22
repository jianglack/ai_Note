package com.ainote.app.config;

import com.ainote.app.model.BatchArchiveRequest;
import com.ainote.app.model.BatchMoveRequest;
import com.ainote.app.model.BatchNoteRequest;
import com.ainote.app.model.BatchTagRequest;
import com.ainote.app.model.LoginRequest;
import com.ainote.app.model.NoteRequest;
import com.ainote.app.model.RegisterRequest;
import com.ainote.app.model.ResetPasswordRequest;
import com.ainote.app.model.TagRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void loginRequest_rejectsBlankUsername() {
        LoginRequest req = new LoginRequest();
        req.setUsername("");
        req.setPassword("password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    @Test
    void loginRequest_rejectsBlankPassword() {
        LoginRequest req = new LoginRequest();
        req.setUsername("user1");
        req.setPassword("");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void loginRequest_acceptsValid() {
        LoginRequest req = new LoginRequest();
        req.setUsername("user1");
        req.setPassword("password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    @Test
    void registerRequest_rejectsInvalidEmail() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("user1");
        req.setEmail("not-an-email");
        req.setPassword("password123");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void registerRequest_rejectsShortPassword() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("user1");
        req.setEmail("user@example.com");
        req.setPassword("12345");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void resetPasswordRequest_rejectsBlankUsername() {
        ResetPasswordRequest req = new ResetPasswordRequest("", "user@example.com", "newpass123");

        Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    @Test
    void resetPasswordRequest_rejectsShortNewPassword() {
        ResetPasswordRequest req = new ResetPasswordRequest("user1", "user@example.com", "123");

        Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }

    @Test
    void noteRequest_rejectsBlankTitle() {
        NoteRequest req = new NoteRequest();
        req.setTitle("");
        req.setContent("some content");

        Set<ConstraintViolation<NoteRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
    }

    @Test
    void noteRequest_acceptsNullContent() {
        NoteRequest req = new NoteRequest();
        req.setTitle("My Note");

        Set<ConstraintViolation<NoteRequest>> violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    @Test
    void tagRequest_rejectsBlankName() {
        TagRequest req = new TagRequest();
        req.setName("");

        Set<ConstraintViolation<TagRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void batchNoteRequest_rejectsEmptyNoteIds() {
        BatchNoteRequest req = new BatchNoteRequest();
        req.setNoteIds(List.of());

        Set<ConstraintViolation<BatchNoteRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("noteIds"));
    }

    @Test
    void batchNoteRequest_rejectsNullNoteIds() {
        BatchNoteRequest req = new BatchNoteRequest();
        req.setNoteIds(null);

        Set<ConstraintViolation<BatchNoteRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("noteIds"));
    }

    @Test
    void batchTagRequest_rejectsBlankTagName() {
        BatchTagRequest req = new BatchTagRequest();
        req.setNoteIds(List.of("note-1"));
        req.setTagName("");

        Set<ConstraintViolation<BatchTagRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("tagName"));
    }

    @Test
    void batchArchiveRequest_rejectsEmptyNoteIds() {
        BatchArchiveRequest req = new BatchArchiveRequest();
        req.setNoteIds(List.of());
        req.setArchive(true);

        Set<ConstraintViolation<BatchArchiveRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("noteIds"));
    }

    @Test
    void authController_hasValidAnnotation() {
        assertMethodParamHasValid(
                com.ainote.app.controller.AuthController.class, "login", LoginRequest.class);
        assertMethodParamHasValid(
                com.ainote.app.controller.AuthController.class, "register", RegisterRequest.class);
        assertMethodParamHasValid(
                com.ainote.app.controller.AuthController.class, "resetPassword", ResetPasswordRequest.class);
    }

    @Test
    void noteController_hasValidAnnotation() {
        assertMethodParamHasValid(
                com.ainote.app.controller.NoteController.class, "create", NoteRequest.class);
        assertMethodParamHasValid(
                com.ainote.app.controller.NoteController.class, "update", NoteRequest.class);
    }

    @Test
    void tagController_hasValidAnnotation() {
        assertMethodParamHasValid(
                com.ainote.app.controller.TagController.class, "create", TagRequest.class);
    }

    @Test
    void batchController_hasValidAnnotation() {
        assertMethodParamHasValid(
                com.ainote.app.controller.BatchController.class, "batchDelete", BatchNoteRequest.class);
        assertMethodParamHasValid(
                com.ainote.app.controller.BatchController.class, "batchPermanentDelete", BatchNoteRequest.class);
        assertMethodParamHasValid(
                com.ainote.app.controller.BatchController.class, "batchRestore", BatchNoteRequest.class);
        assertMethodParamHasValid(
                com.ainote.app.controller.BatchController.class, "batchMove", BatchMoveRequest.class);
        assertMethodParamHasValid(
                com.ainote.app.controller.BatchController.class, "batchAddTag", BatchTagRequest.class);
        assertMethodParamHasValid(
                com.ainote.app.controller.BatchController.class, "batchRemoveTag", BatchTagRequest.class);
        assertMethodParamHasValid(
                com.ainote.app.controller.BatchController.class, "batchArchive", BatchArchiveRequest.class);
    }

    private void assertMethodParamHasValid(Class<?> controllerClass, String methodName, Class<?> dtoType) {
        var method = java.util.Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .filter(m -> java.util.Arrays.asList(m.getParameterTypes()).contains(dtoType))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        String.format("Method %s.%s with param %s not found",
                                controllerClass.getSimpleName(), methodName, dtoType.getSimpleName())));

        var paramTypes = method.getParameterTypes();
        var paramAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i] == dtoType) {
                boolean hasValid = java.util.Arrays.stream(paramAnnotations[i])
                        .anyMatch(a -> a.annotationType() == Valid.class);
                assertThat(hasValid)
                        .as("@Valid missing on %s.%s param %s",
                                controllerClass.getSimpleName(), methodName, dtoType.getSimpleName())
                        .isTrue();
                return;
            }
        }
    }
}
