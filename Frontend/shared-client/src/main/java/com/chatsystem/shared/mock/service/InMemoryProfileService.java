package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.ProfileVisibility;
import com.chatsystem.shared.model.status.UserStatus;
import com.chatsystem.shared.service.ProfileService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.shared.validation.ValidationError;
import com.chatsystem.shared.validation.ValidationResult;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public final class InMemoryProfileService implements ProfileService {
    private final MockDataStore store;
    private final UserSession session;

    public InMemoryProfileService(MockDataStore store, UserSession session) {
        this.store = Objects.requireNonNull(store);
        this.session = Objects.requireNonNull(session);
    }

    @Override
    public ValidationResult<UserAccount> updateProfile(String name, String username, String bio, ProfileVisibility visibility) {
        UserAccount user = session.getCurrentUser();
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !store.getUsers().contains(user)) {
            return ValidationResult.failure(List.of(ValidationError.global("Sign in with an active account first")));
        }
        List<ValidationError> errors = new ArrayList<>();
        if (name == null || name.isBlank()) errors.add(ValidationError.field("displayName", "Display name is required"));
        String normalizedUsername = username == null ? "" : username.strip();
        if (!normalizedUsername.matches("[A-Za-z0-9_]+")) {
            errors.add(ValidationError.field("username", "Use letters, numbers, or underscores for the username"));
        } else if (store.getUsers().stream().anyMatch(u -> u != user && u.getUsername().equalsIgnoreCase(normalizedUsername))) {
            errors.add(ValidationError.field("username", "Username is already in use"));
        }
        if (bio == null || bio.length() > 500) errors.add(ValidationError.field("bio", "Bio must contain at most 500 characters"));
        if (visibility == null) errors.add(ValidationError.field("profileVisibility", "Choose a profile visibility"));
        if (!errors.isEmpty()) return ValidationResult.failure(errors);
        user.updateProfile(name.strip(), normalizedUsername, bio, visibility);
        return ValidationResult.success(user);
    }
}
