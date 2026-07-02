package com.ainote.app.security;

import com.ainote.app.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAccessGuardTest {

    private final SecurityUtils securityUtils = mock(SecurityUtils.class);

    @Test
    void allowsAccess_whenUserIdInAllowlist() {
        AdminAccessGuard guard = new AdminAccessGuard("user-1,user-2", securityUtils);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");

        assertThatCode(guard::checkAdminAccess).doesNotThrowAnyException();
    }

    @Test
    void deniesAccess_whenUserIdNotInAllowlist() {
        AdminAccessGuard guard = new AdminAccessGuard("user-1,user-2", securityUtils);
        when(securityUtils.getCurrentUserId()).thenReturn("user-999");

        assertThatThrownBy(guard::checkAdminAccess).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesAccess_whenAllowlistEmpty() {
        AdminAccessGuard guard = new AdminAccessGuard("", securityUtils);
        when(securityUtils.getCurrentUserId()).thenReturn("any-user");

        assertThatThrownBy(guard::checkAdminAccess).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesAccess_whenAllowlistNull() {
        AdminAccessGuard guard = new AdminAccessGuard(null, securityUtils);
        when(securityUtils.getCurrentUserId()).thenReturn("any-user");

        assertThatThrownBy(guard::checkAdminAccess).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void handlesWhitespaceInAllowlist() {
        AdminAccessGuard guard = new AdminAccessGuard(" user-1 , user-2 ", securityUtils);
        when(securityUtils.getCurrentUserId()).thenReturn("user-2");

        assertThatCode(guard::checkAdminAccess).doesNotThrowAnyException();
    }

    @Test
    void globalExceptionHandler_returnsForbiddenOnAccessDenied() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response =
                handler.handleAccessDenied(new AccessDeniedException("\u6743\u9650\u4e0d\u8db3"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).contains("\u6743\u9650");
    }
}
