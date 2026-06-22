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

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void genericException_doesNotLeakMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleGenericException(new NullPointerException("some.internal.Class at line 42"));

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().get("error").contains("some.internal.Class"));
        assertEquals("\u670d\u52a1\u5668\u5185\u90e8\u9519\u8bef\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5", response.getBody().get("error"));
    }

    @Test
    void illegalArgument_stillReturnsBizMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("\u7b14\u8bb0\u4e0d\u5b58\u5728"));

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("\u7b14\u8bb0\u4e0d\u5b58\u5728", response.getBody().get("error"));
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

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("\u53c2\u6570\u6821\u9a8c\u5931\u8d25", response.getBody().get("error"));
        assertInstanceOf(List.class, response.getBody().get("details"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> details = (List<Map<String, String>>) response.getBody().get("details");
        assertEquals(2, details.size());

        var fields = details.stream().map(d -> d.get("field")).toList();
        assertTrue(fields.contains("username"));
        assertTrue(fields.contains("password"));
    }

    @Test
    void notFoundExceptions_return404() {
        ResponseEntity<Map<String, String>> noSuchElement =
                handler.handleNotFound(new NoSuchElementException("missing"));
        ResponseEntity<Map<String, String>> entityNotFound =
                handler.handleNotFound(new EntityNotFoundException("missing"));

        assertEquals(404, noSuchElement.getStatusCode().value());
        assertEquals(404, entityNotFound.getStatusCode().value());
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
            assertEquals(400, response.getStatusCode().value());
            assertNotNull(response.getBody());
        }
    }

    @Test
    void unsupportedMethod_returns405() {
        ResponseEntity<Map<String, String>> response =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("POST", List.of("GET")));

        assertEquals(405, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @SuppressWarnings("unused")
    void dummyMethod(String param) {
    }
}
