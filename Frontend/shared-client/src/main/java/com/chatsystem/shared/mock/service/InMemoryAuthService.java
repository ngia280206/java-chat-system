package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.UserStatus;
import com.chatsystem.shared.service.AuthService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.shared.validation.ValidationError;
import com.chatsystem.shared.validation.ValidationResult;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Synchronous, process-local demo only: fixed reset code and plaintext in-memory credentials. */
public final class InMemoryAuthService implements AuthService {
    private final MockDataStore store;
    private final ReadOnlyObjectWrapper<UserAccount> current = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper remembered = new ReadOnlyStringWrapper("");
    private final UserSession session = new UserSession(current.getReadOnlyProperty(), remembered.getReadOnlyProperty());
    private UserAccount resetTarget;
    private boolean resetVerified;

    public InMemoryAuthService(MockDataStore store) { this.store = Objects.requireNonNull(store); }

    @Override
    public UserSession getSession() { return session; }

    @Override
    public ValidationResult<UserAccount> login(String email, String password, boolean rememberEmail) {
        List<ValidationError> errors = new ArrayList<>();
        String normalized = validateEmail(email, errors);
        if (password == null || password.isBlank()) errors.add(ValidationError.field("password", "Password is required"));
        if (!errors.isEmpty()) return ValidationResult.failure(errors);
        UserAccount user = findByEmail(normalized);
        if (user == null || !store.matchesMockPassword(user.getId(), password)) {
            return globalFailure("Email or password is incorrect");
        }
        if (user.getStatus() != UserStatus.ACTIVE) return globalFailure("This account is locked or unavailable");
        store.recordMockLogin(user.getId());
        remembered.set(rememberEmail ? user.getEmail() : "");
        current.set(user);
        clearReset();
        return ValidationResult.success(user);
    }

    @Override
    public ValidationResult<UserAccount> register(String name, String email, String password, String confirm, boolean terms) {
        List<ValidationError> errors = new ArrayList<>();
        if (name == null || name.isBlank()) errors.add(ValidationError.field("displayName", "Display name is required"));
        String normalized = validateEmail(email, errors);
        if (findByEmail(normalized) != null) errors.add(ValidationError.field("email", "Email is already registered"));
        validatePassword(password, confirm, "password", errors);
        if (!terms) errors.add(ValidationError.field("termsAccepted", "Accept the terms to register"));
        if (!errors.isEmpty()) return ValidationResult.failure(errors);
        return ValidationResult.success(store.createMockAccount(name.strip(), normalized, password));
    }

    @Override
    public ValidationResult<Void> beginPasswordReset(String email) {
        List<ValidationError> errors = new ArrayList<>();
        String normalized = validateEmail(email, errors);
        if (!errors.isEmpty()) return ValidationResult.failure(errors);
        UserAccount user = findByEmail(normalized);
        if (user == null) return ValidationResult.failure(List.of(ValidationError.field("email", "No account uses this email")));
        if (user.getStatus() != UserStatus.ACTIVE) return globalFailure("This account is locked or unavailable");
        resetTarget = user;
        resetVerified = false;
        return ValidationResult.success(null);
    }

    @Override
    public ValidationResult<Void> verifyResetCode(String code) {
        if (!hasActiveResetTarget()) return globalFailure("Start a password reset for an active account first");
        if (!MockFixtures.RESET_CODE.equals(code)) {
            return ValidationResult.failure(List.of(ValidationError.field("code", "Enter the demo verification code")));
        }
        resetVerified = true;
        return ValidationResult.success(null);
    }

    @Override
    public ValidationResult<Void> resetPassword(String password, String confirm) {
        if (!hasActiveResetTarget() || !resetVerified) return globalFailure("Verify a password reset code first");
        List<ValidationError> errors = new ArrayList<>();
        validatePassword(password, confirm, "newPassword", errors);
        rejectCurrentPassword(resetTarget, password, errors);
        if (!errors.isEmpty()) return ValidationResult.failure(errors);
        store.replaceMockPassword(resetTarget.getId(), password);
        clearReset();
        return ValidationResult.success(null);
    }

    @Override
    public ValidationResult<Void> changePassword(String currentPassword, String password, String confirm) {
        UserAccount user = current.get();
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !store.getUsers().contains(user)) {
            return globalFailure("Sign in with an active account first");
        }
        List<ValidationError> errors = new ArrayList<>();
        if (!store.matchesMockPassword(user.getId(), currentPassword)) {
            errors.add(ValidationError.field("currentPassword", "Current password is incorrect"));
        }
        validatePassword(password, confirm, "newPassword", errors);
        rejectCurrentPassword(user, password, errors);
        if (!errors.isEmpty()) return ValidationResult.failure(errors);
        store.replaceMockPassword(user.getId(), password);
        clearReset();
        return ValidationResult.success(null);
    }

    @Override
    public void logout() {
        clearReset();
        current.set(null);
    }

    private void clearReset() { resetTarget = null; resetVerified = false; }

    private boolean hasActiveResetTarget() {
        return resetTarget != null && resetTarget.getStatus() == UserStatus.ACTIVE && store.getUsers().contains(resetTarget);
    }

    private UserAccount findByEmail(String email) {
        return store.getUsers().stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst().orElse(null);
    }

    private static String validateEmail(String email, List<ValidationError> errors) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            errors.add(ValidationError.field("email", "Enter a valid email address"));
        }
        return normalized;
    }

    private static void validatePassword(String password, String confirm, String field, List<ValidationError> errors) {
        if (password == null || password.length() < 8 || password.chars().noneMatch(Character::isDigit)
                || password.chars().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            errors.add(ValidationError.field(field, "Use at least 8 characters, one number, and one special character"));
        }
        if (confirm == null || confirm.isBlank() || !confirm.equals(password)) {
            errors.add(ValidationError.field("confirmPassword", "Passwords must match"));
        }
    }

    private void rejectCurrentPassword(UserAccount user, String password, List<ValidationError> errors) {
        if (store.matchesMockPassword(user.getId(), password)) {
            errors.add(ValidationError.field("newPassword", "Choose a password different from the current password"));
        }
    }

    private static <T> ValidationResult<T> globalFailure(String message) {
        return ValidationResult.failure(List.of(ValidationError.global(message)));
    }
}
