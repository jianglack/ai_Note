package com.ainote.auth.controller;

import com.ainote.auth.entity.User;
import com.ainote.auth.filter.JwtAuthFilter;
import com.ainote.auth.model.AuthResponse;
import com.ainote.auth.model.LoginRequest;
import com.ainote.auth.model.RegisterRequest;
import com.ainote.auth.model.ResetPasswordRequest;
import com.ainote.auth.repository.UserRepository;
import com.ainote.auth.service.AuthService;
import com.ainote.common.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, JwtUtils jwtUtils, UserRepository userRepository) {
        this.authService = authService;
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.ok(authService.register(request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(authService.login(request));
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String jwt = JwtAuthFilter.getJwtFromRequest(request);
        if (jwt != null) {
            authService.logout(jwt);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request.getUsername(), request.getEmail(), request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        String jwt = JwtAuthFilter.getJwtFromRequest(request);
        if (jwt == null || !jwtUtils.validateToken(jwt)) {
            return ResponseEntity.status(401).build();
        }

        String userId = jwtUtils.getSubject(jwt);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        return ResponseEntity.ok(Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail()
        ));
    }
}
