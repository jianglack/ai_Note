package com.ainote.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void genericException_doesNotLeakMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleGenericException(new NullPointerException("some.internal.Class at line 42"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).doesNotContain("some.internal.Class");
        assertThat(response.getBody().get("error")).contains("\u670d\u52a1\u5668\u5185\u90e8\u9519\u8bef");
    }

    @Test
    void illegalArgument_doesNotLeakOriginalMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("\u7b14\u8bb0\u4e0d\u5b58\u5728"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).contains("\u8bf7\u6c42\u53c2\u6570");
    }

    @Test
    void methodArgumentNotValid_returnsFieldErrors() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "loginRequest");
        bindingResult.addError(new FieldError("loginRequest", "username", "\u4e0d\u80fd\u4e3a\u7a7a"));
        bindingResult.addError(new FieldError("loginRequest", "password", "\u4e0d\u80fd\u4e3a\u7a7a"));

        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        var ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error").toString()).contains("\u6821\u9a8c\u5931\u8d25");
        assertThat(response.getBody().get("details")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> details = (List<Map<String, String>>) response.getBody().get("details");
        assertThat(details).hasSize(2);

        var fields = details.stream().map(d -> d.get("field")).toList();
        assertThat(fields).contains("username", "password");
    }

    @Test
    void notFoundExceptions_return404() {
        ResponseEntity<Map<String, String>> noSuchElement =
                handler.handleNotFound(new NoSuchElementException("missing"));
        ResponseEntity<Map<String, String>> entityNotFound =
                handler.handleNotFound(new EntityNotFoundException("missing"));
        ResponseEntity<Map<String, String>> staticResourceNotFound =
                handler.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "/v3/api-docs"));

        assertThat(noSuchElement.getStatusCode().value()).isEqualTo(404);
        assertThat(entityNotFound.getStatusCode().value()).isEqualTo(404);
        assertThat(staticResourceNotFound.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void requestShapeExceptions_return400() throws Exception {
        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);

        List<Exception> exceptions = List.of(
                new HttpMessageNotReadableException("bad json"),
                new MethodArgumentTypeMismatchException("abc", Integer.class, "id", param, new NumberFormatException()),
                new MissingServletRequestParameterException("q", "String"),
                new ConstraintViolationException(Set.of())
        );

        for (Exception exception : exceptions) {
            ResponseEntity<Map<String, String>> response = handler.handleBadRequest(exception);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).isNotNull();
        }
    }

    @Test
    void unsupportedMethod_returns405() {
        ResponseEntity<Map<String, String>> response =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("POST", List.of("GET")));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void maxUploadSizeExceeded_returns413WithoutLeakingMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).contains("\u8bf7\u6c42\u4f53");
    }

    @SuppressWarnings("unused")
    void dummyMethod(String param) {
    }
}
