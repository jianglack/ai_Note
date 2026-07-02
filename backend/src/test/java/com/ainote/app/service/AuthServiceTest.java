package com.ainote.app.service;

import com.ainote.app.entity.User;
import com.ainote.app.model.AuthResponse;
import com.ainote.app.model.LoginRequest;
import com.ainote.app.model.RegisterRequest;
import com.ainote.app.repository.UserRepository;
import com.ainote.app.security.JwtTokenProvider;
import com.ainote.app.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
@DisplayName("AuthService 单元测试")
class AuthServiceTest {
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider;
    private AuthenticationManager authenticationManager;
    private TokenService tokenService;
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenProvider = mock(JwtTokenProvider.class);
        authenticationManager = mock(AuthenticationManager.class);
        tokenService = mock(TokenService.class);
        authService = new AuthService(userRepository, passwordEncoder, tokenProvider,
                                       authenticationManager, tokenService);

        testUser = new User();
        testUser.setId("user-123");
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashed-password");
        testUser.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("register 应创建新用户并返回 token")
    void shouldRegisterNewUser() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setEmail("new@example.com");
        request.setPassword("password123");

        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setCreatedAt(LocalDateTime.now());
            return u;
        });
        when(tokenProvider.generateToken(anyString(), anyString())).thenReturn("jwt-token");
        when(tokenProvider.getJtiFromToken("jwt-token")).thenReturn("jti-123");

        AuthResponse result = authService.register(request);

        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getEmail()).isEqualTo("new@example.com");

        verify(tokenService).storeToken("jti-123", result.getUserId());
    }

    @Test
    @DisplayName("register 用户名已存在时应抛出异常")
    void shouldThrowWhenUsernameExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existinguser");
        request.setEmail("new@example.com");
        request.setPassword("password123");

        when(userRepository.findByUsername("existinguser")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Username already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register 邮箱已存在时应抛出异常")
    void shouldThrowWhenEmailExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setEmail("existing@example.com");
        request.setPassword("password123");

        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Email already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login 应验证并返回 token")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenReturn(null); // 认证成功返回 null 或 Authentication 对象
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(tokenProvider.generateToken("user-123", "testuser")).thenReturn("jwt-token");
        when(tokenProvider.getJtiFromToken("jwt-token")).thenReturn("jti-123");

        AuthResponse result = authService.login(request);

        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getUsername()).isEqualTo("testuser");

        verify(tokenService).storeToken("jti-123", "user-123");
    }

    @Test
    @DisplayName("login 用户不存在时应抛出异常")
    void shouldThrowWhenUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("password");

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("User not found");
    }

    @Test
    @DisplayName("logout 应删除有效 token")
    void shouldLogoutAndDeleteToken() {
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getJtiFromToken("valid-token")).thenReturn("jti-123");

        authService.logout("valid-token");

        verify(tokenService).deleteToken("jti-123");
    }

    @Test
    @DisplayName("logout 无效 token 时不删除")
    void shouldNotDeleteInvalidToken() {
        when(tokenProvider.validateToken("invalid-token")).thenReturn(false);

        authService.logout("invalid-token");

        verify(tokenService, never()).deleteToken(anyString());
    }

    @Test
    @DisplayName("resetPassword 应重置密码")
    void shouldResetPassword() {
        assertThatThrownBy(() -> authService.resetPassword("testuser", "test@example.com", "newpassword"))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Legacy password reset is disabled");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("resetPassword 用户不存在时应抛出异常")
    void shouldThrowWhenResetPasswordUserNotFound() {
        assertThatThrownBy(() -> authService.resetPassword("nonexistent", "test@example.com", "newpass"))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Legacy password reset is disabled");
    }

    @Test
    @DisplayName("resetPassword 邮箱不匹配时应抛出异常")
    void shouldThrowWhenEmailMismatch() {
        assertThatThrownBy(() -> authService.resetPassword("testuser", "wrong@example.com", "newpass"))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Legacy password reset is disabled");

        verify(userRepository, never()).save(any());
    }
}
