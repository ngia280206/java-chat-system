package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.model.UserSettings;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.UserStatus;
import com.chatsystem.shared.service.SettingsService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.shared.validation.ValidationError;
import com.chatsystem.shared.validation.ValidationResult;
import java.util.List;
import java.util.Objects;

public final class InMemorySettingsService implements SettingsService {
    private final MockDataStore store;
    private final UserSession session;

    public InMemorySettingsService(MockDataStore store, UserSession session) {
        this.store = Objects.requireNonNull(store);
        this.session = Objects.requireNonNull(session);
    }

    @Override
    public ValidationResult<UserSettings> getSettings() {
        UserAccount user = session.getCurrentUser();
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !store.getUsers().contains(user)) {
            return ValidationResult.failure(List.of(ValidationError.global("Sign in with an active account first")));
        }
        return store.getSettings().stream().filter(s -> s.getUserId() == user.getId()).findFirst()
                .map(ValidationResult::success)
                .orElseGet(() -> ValidationResult.failure(List.of(ValidationError.global("Account settings are unavailable"))));
    }

    @Override
    public ValidationResult<UserSettings> updateSettings(boolean desktop, boolean sound, boolean previews) {
        ValidationResult<UserSettings> result = getSettings();
        if (result.isSuccess()) result.value().orElseThrow().updatePreferences(desktop, sound, previews);
        return result;
    }
}
