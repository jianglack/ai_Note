package com.ainote.app.security;

import com.ainote.app.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAccessGuardTest {

    private final SecurityUtils securityUtils = mock(SecurityUtils.class);

    @Test
    void allowsAccess_whenUserIdInAllowlist() {
        AdminAccessGuard guard = new AdminAccessGuard("user-1,user-2", securityUtils);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");

        assertDoesNotThrow(guard::checkAdminAccess);
    }

    @Test
    void deniesAccess_whenUserIdNotInAllowlist() {
        AdminAccessGuard guard = new AdminAccessGuard("user-1,user-2", securityUtils);
        when(securityUtils.getCurrentUserId()).thenReturn("user-999");

        assertThrows(AccessDeniedException.class, guard::checkAdminAccess);
    }

    @Test
    void deniesAccess_whenAllowlistEmpty() {
        AdminAccessGuard guard = new AdminAccessGuard("", securityUtils);
        when(securityUtils.getCurrentUserId()).thenReturn("any-user");

        assertThrows(AccessDeniedException.class, guard::checkAdminAccess);
    }

    @Test
    void deniesAccess_whenAllowlistNull() {
        AdminAccessGuard guard = new AdminAccessGuard(null, securityUtils);
        when(securityUtils.getCurrentUserId()).thenReturn("any-user");

        assertThrows(AccessDeniedException.class, guard::checkAdminAccess);
    }

    @Test
    void handlesWhitespaceInAllowlist() {
        AdminAccessGuard guard = new AdminAccessGuard(" user-1 , user-2 ", securityUtils);
        when(securityUtils.getCurrentUserId()).thenReturn("user-2");

        assertDoesNotThrow(guard::checkAdminAccess);
    }

    @Test
    void globalExceptionHandler_returnsForbiddenOnAccessDenied() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response =
                handler.handleAccessDenied(new AccessDeniedException("\u6743\u9650\u4e0d\u8db3"));

        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("\u6743\u9650\u4e0d\u8db3", response.getBody().get("error"));
    }
}
