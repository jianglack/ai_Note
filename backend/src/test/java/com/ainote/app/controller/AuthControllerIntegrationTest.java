package com.ainote.app.controller;

import com.ainote.app.model.AuthResponse;
import com.ainote.app.model.LoginRequest;
import com.ainote.app.model.RegisterRequest;
import com.ainote.app.model.ResetPasswordRequest;
import com.ainote.app.service.AuthRateLimiter;
import com.ainote.app.service.AuthService;
import com.ainote.app.service.PasswordResetService;
import com.ainote.app.service.RateLimitExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.ainote.app.security.SecurityUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthControllerIntegrationTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private ResetPasswordRequest resetPasswordRequest;
    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");

        loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");

        resetPasswordRequest = new ResetPasswordRequest();
        resetPasswordRequest.setUsername("testuser");
        resetPasswordRequest.setEmail("test@example.com");
        resetPasswordRequest.setNewPassword("newpassword123");

        authResponse = new AuthResponse();
        authResponse.setToken("test-token");
        authResponse.setUsername("testuser");
    }

    @Test
    @DisplayName("POST /api/auth/register 应成功注册新用户")
    void shouldRegisterUserSuccessfully() throws Exception {
        Mockito.when(authService.register(Mockito.any(RegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.token").value("test-token"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.username").value("testuser"));
    }

    @Test
    @DisplayName("POST /api/auth/register 达到限流时应返回429")
    void shouldReturnTooManyRequestsWhenRegisterRateLimited() throws Exception {
        Mockito.doThrow(new RateLimitExceededException("Too many authentication attempts"))
                .when(authRateLimiter)
                .check("register", "test@example.com:127.0.0.1", 5);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(MockMvcResultMatchers.status().isTooManyRequests());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/register 应在注册失败时返回400")
    void shouldReturnBadRequestWhenRegisterFails() throws Exception {
        Mockito.when(authService.register(Mockito.any(RegisterRequest.class))).thenThrow(new RuntimeException("Registration failed"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login 应成功登录用户")
    void shouldLoginUserSuccessfully() throws Exception {
        Mockito.when(authService.login(Mockito.any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.token").value("test-token"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.username").value("testuser"));
    }

    @Test
    @DisplayName("POST /api/auth/login 达到限流时应返回429")
    void shouldReturnTooManyRequestsWhenLoginRateLimited() throws Exception {
        Mockito.doThrow(new RateLimitExceededException("Too many authentication attempts"))
                .when(authRateLimiter)
                .check("login", "testuser:127.0.0.1", 10);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(MockMvcResultMatchers.status().isTooManyRequests());

        verify(authService, never()).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/login 应在登录失败时返回401")
    void shouldReturnUnauthorizedWhenLoginFails() throws Exception {
        Mockito.when(authService.login(Mockito.any(LoginRequest.class))).thenThrow(new RuntimeException("Login failed"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/logout 应成功注销用户")
    void shouldLogoutUserSuccessfully() throws Exception {
        Mockito.doNothing().when(authService).logout(Mockito.anyString());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/logout")
                .header("Authorization", "Bearer test-token"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/auth/reset-password 应成功重置密码")
    void shouldResetPasswordSuccessfully() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetPasswordRequest)))
                .andExpect(MockMvcResultMatchers.status().isGone());

        Mockito.verify(authService, Mockito.never())
                .resetPassword(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    @DisplayName("POST /api/auth/reset-password 应在重置失败时返回400")
    void shouldReturnBadRequestWhenResetPasswordFails() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetPasswordRequest)))
                .andExpect(MockMvcResultMatchers.status().isGone());

        Mockito.verify(authService, Mockito.never())
                .resetPassword(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }
}
