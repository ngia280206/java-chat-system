package com.chatsystem.shared.model;

import com.chatsystem.shared.model.status.*;
import javafx.beans.property.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** UI domain state. Domain mutations are called by services, never controllers. */
public final class UserAccount {
    private final long id;
    private final ReadOnlyStringWrapper email = new ReadOnlyStringWrapper(this, "email");
    private final Instant createdAt;
    private final ReadOnlyStringWrapper displayName = new ReadOnlyStringWrapper(this, "displayName");
    private final ReadOnlyStringWrapper username = new ReadOnlyStringWrapper(this, "username");
    private final ReadOnlyStringWrapper bio = new ReadOnlyStringWrapper(this, "bio");
    private final ReadOnlyStringWrapper address = new ReadOnlyStringWrapper(this, "address", "");
    private final ReadOnlyObjectWrapper<LocalDate> dateOfBirth = new ReadOnlyObjectWrapper<>(this, "dateOfBirth");
    private final ReadOnlyStringWrapper gender = new ReadOnlyStringWrapper(this, "gender", "Not specified");
    /** Demo presence only; account ACTIVE/LOCKED status is unrelated to online presence. */
    private final ReadOnlyBooleanWrapper online = new ReadOnlyBooleanWrapper(this, "online", false);
    private final ReadOnlyObjectWrapper<UserStatus> status = new ReadOnlyObjectWrapper<>(this, "status");
    private final ReadOnlyObjectWrapper<ProfileVisibility> profileVisibility =
            new ReadOnlyObjectWrapper<>(this, "profileVisibility", ProfileVisibility.FRIENDS_ONLY);

    public UserAccount(long id, String email, String displayName, String username,
                       String bio, UserStatus status, Instant createdAt) {
        this.id = id;
        this.email.set(Objects.requireNonNull(email));
        this.createdAt = Objects.requireNonNull(createdAt);
        updateProfile(displayName, username, bio);
        setStatus(status);
    }

    public long getId() { return id; }

    public String getEmail() { return email.get(); }
    public ReadOnlyStringProperty emailProperty() { return email.getReadOnlyProperty(); }
    public void updateEmail(String value) { email.set(Objects.requireNonNull(value)); }

    public Instant getCreatedAt() { return createdAt; }

    public String getDisplayName() { return displayName.get(); }
    public ReadOnlyStringProperty displayNameProperty() { return displayName.getReadOnlyProperty(); }

    public String getUsername() { return username.get(); }
    public ReadOnlyStringProperty usernameProperty() { return username.getReadOnlyProperty(); }

    public String getBio() { return bio.get(); }
    public ReadOnlyStringProperty bioProperty() { return bio.getReadOnlyProperty(); }

    public String getAddress() { return address.get(); }
    public ReadOnlyStringProperty addressProperty() { return address.getReadOnlyProperty(); }
    public LocalDate getDateOfBirth() { return dateOfBirth.get(); }
    public ReadOnlyObjectProperty<LocalDate> dateOfBirthProperty() { return dateOfBirth.getReadOnlyProperty(); }
    public String getGender() { return gender.get(); }
    public ReadOnlyStringProperty genderProperty() { return gender.getReadOnlyProperty(); }
    public boolean isOnline() { return online.get(); }
    public ReadOnlyBooleanProperty onlineProperty() { return online.getReadOnlyProperty(); }
    public void setOnline(boolean value) { online.set(value); }
    public void updatePersonalDetails(String address, LocalDate dob, String gender) {
        this.address.set(Objects.requireNonNullElse(address, "").strip());
        dateOfBirth.set(dob);
        this.gender.set(Objects.requireNonNullElse(gender, "Not specified"));
    }

    public UserStatus getStatus() { return status.get(); }
    public ReadOnlyObjectProperty<UserStatus> statusProperty() { return status.getReadOnlyProperty(); }

    public ProfileVisibility getProfileVisibility() { return profileVisibility.get(); }
    public ReadOnlyObjectProperty<ProfileVisibility> profileVisibilityProperty() { return profileVisibility.getReadOnlyProperty(); }

    public void updateProfile(String displayName, String username, String bio) {
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(username);
        Objects.requireNonNull(bio);
        this.displayName.set(displayName);
        this.username.set(username);
        this.bio.set(bio);
    }

    public void setStatus(UserStatus status) { this.status.set(Objects.requireNonNull(status)); }

    public void updateProfile(String displayName, String username, String bio, ProfileVisibility visibility) {
        Objects.requireNonNull(visibility);
        updateProfile(displayName, username, bio);
        profileVisibility.set(visibility);
    }
}
