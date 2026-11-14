package com.chatsystem.shared.service;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.shared.validation.ValidationResult;

/** Account workflows; implementations supply authentication policy and lifetime. */
public interface AuthService {
    UserSession getSession();
    ValidationResult<UserAccount> login(String email, String password, boolean rememberEmail);
    /** Creates an account without signing in. */
    ValidationResult<UserAccount> register(String displayName, String email, String password,
                                           String confirmPassword, boolean termsAccepted);
    ValidationResult<Void> beginPasswordReset(String email);
    ValidationResult<Void> verifyResetCode(String code);
    ValidationResult<Void> resetPassword(String newPassword, String confirmPassword);
    ValidationResult<Void> changePassword(String currentPassword, String newPassword, String confirmPassword);
    /** Clears authenticated and reset state; an opted-in remembered email remains. */
    void logout();
}
