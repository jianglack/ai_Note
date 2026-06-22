package com.ainote.app.service;

import com.ainote.app.entity.User;
import com.ainote.app.model.AuthResponse;
import com.ainote.app.model.LoginRequest;
import com.ainote.app.model.RegisterRequest;
import com.ainote.app.repository.UserRepository;
import com.ainote.app.security.JwtTokenProvider;
import com.ainote.app.security.TokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider, AuthenticationManager authenticationManager,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        String token = tokenProvider.generateToken(saved.getId(), saved.getUsername());
        tokenService.storeToken(tokenProvider.getJtiFromToken(token), saved.getId());

        return new AuthResponse(token, saved.getId(), saved.getUsername(), saved.getEmail());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = tokenProvider.generateToken(user.getId(), user.getUsername());
        tokenService.storeToken(tokenProvider.getJtiFromToken(token), user.getId());

        return new AuthResponse(token, user.getId(), user.getUsername(), user.getEmail());
    }

    public void logout(String token) {
        if (tokenProvider.validateToken(token)) {
            tokenService.deleteToken(tokenProvider.getJtiFromToken(token));
        }
    }

    /**
     * 找回密码：验证用户名和邮箱，然后重置密码
     */
    public void resetPassword(String username, String email, String newPassword) {
        throw new UnsupportedOperationException("Legacy password reset is disabled");
    }
}
