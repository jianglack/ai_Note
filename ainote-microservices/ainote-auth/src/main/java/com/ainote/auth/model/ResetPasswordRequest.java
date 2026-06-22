package com.ainote.auth.model;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String username;
    private String email;
    private String newPassword;
}
