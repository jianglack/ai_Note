package com.ainote.app.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {
    @NotBlank
    @Size(max = 100)
    private String username;
    @Email
    @NotBlank
    @Size(max = 254)
    private String email;
    @NotBlank
    @Size(min = 15, max = 100)
    private String newPassword;

    public ResetPasswordRequest() {
    }

    public ResetPasswordRequest(String username, String email, String newPassword) {
        this.username = username;
        this.email = email;
        this.newPassword = newPassword;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
