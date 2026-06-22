package com.ainote.app.service;

import com.ainote.app.entity.User;

public interface PasswordResetNotifier {

    void sendPasswordResetToken(User user, String rawToken);
}
