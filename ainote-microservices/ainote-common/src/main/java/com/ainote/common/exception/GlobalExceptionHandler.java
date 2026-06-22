package com.ainote.common.exception;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.getErrorCode().name());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ErrorCode.INTERNAL_ERROR.name());

        if ("dev".equals(activeProfile)) {
            body.put("message", ex.getMessage());
        } else {
            body.put("message", "服务器内部错误");
        }

        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(body);
    }
}
