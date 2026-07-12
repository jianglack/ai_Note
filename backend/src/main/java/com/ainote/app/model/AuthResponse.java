package com.ainote.app.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class AuthResponse {
    @JsonIgnore
    private String token;
    private String userId;
    private String username;
    private String email;

    public AuthResponse() {}

    public AuthResponse(String token, String userId, String username, String email) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.email = email;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
