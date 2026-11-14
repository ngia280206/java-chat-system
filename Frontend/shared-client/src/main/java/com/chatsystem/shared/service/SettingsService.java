package com.chatsystem.shared.service;

import com.chatsystem.shared.model.UserSettings;
import com.chatsystem.shared.validation.ValidationResult;

public interface SettingsService {
    ValidationResult<UserSettings> getSettings();
    /** Saves explicitly; editing or discarding local form values has no effect. */
    ValidationResult<UserSettings> updateSettings(boolean desktopNotifications, boolean soundEnabled,
                                                 boolean messagePreviews);
}
