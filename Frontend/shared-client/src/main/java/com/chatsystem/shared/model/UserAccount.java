package com.chatsystem.shared.model;

import com.chatsystem.shared.model.status.*;
import javafx.beans.property.*;
import java.time.Instant;
import java.util.Objects;

/** UI domain state. Domain mutations are called by services, never controllers. */
public final class UserAccount {
    private final long id;
    private final String email;
    private final Instant createdAt;
    private final ReadOnlyStringWrapper displayName = new ReadOnlyStringWrapper(this, "displayName");
    private final ReadOnlyStringWrapper username = new ReadOnlyStringWrapper(this, "username");
    private final ReadOnlyStringWrapper bio = new ReadOnlyStringWrapper(this, "bio");
    private final ReadOnlyObjectWrapper<UserStatus> status = new ReadOnlyObjectWrapper<>(this, "status");

    public UserAccount(long id, String email, String displayName, String username,
                       String bio, UserStatus status, Instant createdAt) {
        this.id = id;
        this.email = Objects.requireNonNull(email);
        this.createdAt = Objects.requireNonNull(createdAt);
        updateProfile(displayName, username, bio);
        setStatus(status);
    }

    public long getId() { return id; }

    public String getEmail() { return email; }

    public Instant getCreatedAt() { return createdAt; }

    public String getDisplayName() { return displayName.get(); }
    public ReadOnlyStringProperty displayNameProperty() { return displayName.getReadOnlyProperty(); }

    public String getUsername() { return username.get(); }
    public ReadOnlyStringProperty usernameProperty() { return username.getReadOnlyProperty(); }

    public String getBio() { return bio.get(); }
    public ReadOnlyStringProperty bioProperty() { return bio.getReadOnlyProperty(); }

    public UserStatus getStatus() { return status.get(); }
    public ReadOnlyObjectProperty<UserStatus> statusProperty() { return status.getReadOnlyProperty(); }

    public void updateProfile(String displayName, String username, String bio) {
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(username);
        Objects.requireNonNull(bio);
        this.displayName.set(displayName);
        this.username.set(username);
        this.bio.set(bio);
    }

    public void setStatus(UserStatus status) { this.status.set(Objects.requireNonNull(status)); }
}
