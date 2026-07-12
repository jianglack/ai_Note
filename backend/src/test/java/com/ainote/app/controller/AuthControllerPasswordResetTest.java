package com.ainote.app.controller;

import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AuthRateLimiter;
import com.ainote.app.service.AuthService;
import com.ainote.app.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerPasswordResetTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private AuthRateLimiter authRateLimiter;

    @MockBean
    private SecurityUtils securityUtils;

    @Test
    void legacyResetPasswordEndpoint_isGoneAndDoesNotChangePassword() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "victim",
                                  "email": "victim@example.com",
                                  "newPassword": "new-secure-password-123"
                                }
                                """))
                .andExpect(status().isGone());

        verify(authService, never()).resetPassword(anyString(), anyString(), anyString());
    }

    @Test
    void requestPasswordReset_returnsAcceptedWithoutLeakingAccountExistence() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "victim@example.com"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(passwordResetService).requestReset("victim@example.com", "127.0.0.1", null);
    }

    @Test
    void requestPasswordReset_returnsServiceUnavailableWhenDeliveryIsNotConfigured() throws Exception {
        Mockito.doThrow(new UnsupportedOperationException("Password reset delivery is not configured"))
                .when(passwordResetService)
                .requestReset("victim@example.com", "127.0.0.1", null);

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "victim@example.com"
                                }
                                """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void confirmPasswordReset_usesOneTimeTokenAndNewPassword() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "reset-token",
                                  "newPassword": "new-secure-password-123"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(passwordResetService).confirmReset("reset-token", "new-secure-password-123");
    }

    @Test
    void confirmPasswordReset_returnsBadRequestForInvalidToken() throws Exception {
        Mockito.doThrow(new IllegalArgumentException("Invalid or expired reset token"))
                .when(passwordResetService).confirmReset("bad-token", "new-secure-password-123");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "bad-token",
                                  "newPassword": "new-secure-password-123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
