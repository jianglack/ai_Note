package com.ainote.app.controller;

import com.ainote.app.model.AuthResponse;
import com.ainote.app.model.LoginRequest;
import com.ainote.app.model.PasswordResetConfirmRequest;
import com.ainote.app.model.PasswordResetRequest;
import com.ainote.app.model.RegisterRequest;
import com.ainote.app.model.ResetPasswordRequest;
import com.ainote.app.security.JwtAuthenticationFilter;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AuthRateLimiter;
import com.ainote.app.service.AuthService;
import com.ainote.app.service.PasswordResetService;
import com.ainote.app.service.RateLimitExceededException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "用户注册、登录、注销、找回密码等认证相关接口")
public class AuthController {

    private final AuthService authService;
    private final SecurityUtils securityUtils;
    private final PasswordResetService passwordResetService;
    private final AuthRateLimiter authRateLimiter;

    @Value("${app.auth.cookie.secure:false}")
    private boolean authCookieSecure;

    @Value("${app.auth.cookie.same-site:Lax}")
    private String authCookieSameSite;

    @Value("${app.jwt.expiration:86400000}")
    private long jwtExpirationMillis;

    public AuthController(AuthService authService, SecurityUtils securityUtils,
                          PasswordResetService passwordResetService,
                          AuthRateLimiter authRateLimiter) {
        this.authService = authService;
        this.securityUtils = securityUtils;
        this.passwordResetService = passwordResetService;
        this.authRateLimiter = authRateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest servletRequest) {
        try {
            authRateLimiter.check("register", request.getEmail() + ":" + servletRequest.getRemoteAddr(), 5);
            return withAuthCookie(authService.register(request));
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest servletRequest) {
        try {
            authRateLimiter.check("login", request.getUsername() + ":" + servletRequest.getRemoteAddr(), 10);
            return withAuthCookie(authService.login(request));
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "用户注销", description = "注销当前用户，清除 Token")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String jwt = JwtAuthenticationFilter.getJwtFromRequest(request);
        if (jwt != null) {
            authService.logout(jwt);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredAuthCookie().toString())
                .build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "重置密码", description = "通过用户名和邮箱重置密码")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                     HttpServletRequest servletRequest) {
        try {
            passwordResetService.requestReset(
                    request.getEmail(),
                    servletRequest.getRemoteAddr(),
                    servletRequest.getHeader("User-Agent"));
            return ResponseEntity.accepted().build();
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        try {
            passwordResetService.confirmReset(request.getToken(), request.getNewPassword());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of("token", csrfToken.getToken()));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> currentUser() {
        return ResponseEntity.ok(authService.getCurrentUser(securityUtils.getCurrentUserId()));
    }

    private ResponseEntity<AuthResponse> withAuthCookie(AuthResponse response) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookie(response.getToken()).toString())
                .body(response);
    }

    private ResponseCookie authCookie(String token) {
        return ResponseCookie.from(JwtAuthenticationFilter.AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite(authCookieSameSite)
                .path("/")
                .maxAge(Duration.ofMillis(jwtExpirationMillis))
                .build();
    }

    private ResponseCookie expiredAuthCookie() {
        return ResponseCookie.from(JwtAuthenticationFilter.AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite(authCookieSameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
