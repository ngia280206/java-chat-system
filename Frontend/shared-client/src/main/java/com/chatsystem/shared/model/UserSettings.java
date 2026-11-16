package com.chatsystem.shared.model;

import com.chatsystem.shared.model.status.*;
import javafx.beans.property.*;
import java.time.Instant;
import java.util.Objects;

/** UI domain state. Domain mutations are called by services, never controllers. */
public final class UserSettings {
    private final long userId;
    private final ReadOnlyBooleanWrapper desktopNotifications = new ReadOnlyBooleanWrapper(this, "desktopNotifications");
    private final ReadOnlyBooleanWrapper soundEnabled = new ReadOnlyBooleanWrapper(this, "soundEnabled");
    private final ReadOnlyBooleanWrapper messagePreviews = new ReadOnlyBooleanWrapper(this, "messagePreviews");

    public UserSettings(long userId) {
        this.userId = userId;
        updatePreferences(true, false, true);
    }

    public long getUserId() { return userId; }

    public boolean isDesktopNotifications() { return desktopNotifications.get(); }
    public ReadOnlyBooleanProperty desktopNotificationsProperty() { return desktopNotifications.getReadOnlyProperty(); }

    public boolean isSoundEnabled() { return soundEnabled.get(); }
    public ReadOnlyBooleanProperty soundEnabledProperty() { return soundEnabled.getReadOnlyProperty(); }

    public boolean isMessagePreviews() { return messagePreviews.get(); }
    public ReadOnlyBooleanProperty messagePreviewsProperty() { return messagePreviews.getReadOnlyProperty(); }

    public void updatePreferences(boolean desktopNotifications, boolean soundEnabled, boolean messagePreviews) {
        this.desktopNotifications.set(desktopNotifications);
        this.soundEnabled.set(soundEnabled);
        this.messagePreviews.set(messagePreviews);
    }
}
