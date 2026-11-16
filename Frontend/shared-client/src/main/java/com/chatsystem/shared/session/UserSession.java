package com.chatsystem.shared.session;

import com.chatsystem.shared.model.UserAccount;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;

import java.util.Objects;

/** Read-only observable account state. The auth service owns the backing properties. */
public final class UserSession {
    private final ReadOnlyObjectProperty<UserAccount> currentUser;
    private final ReadOnlyStringProperty rememberedEmail;
    private final ReadOnlyBooleanWrapper authenticated = new ReadOnlyBooleanWrapper(this, "authenticated");

    public UserSession(ReadOnlyObjectProperty<UserAccount> currentUser, ReadOnlyStringProperty rememberedEmail) {
        this.currentUser = Objects.requireNonNull(currentUser);
        this.rememberedEmail = Objects.requireNonNull(rememberedEmail);
        authenticated.bind(currentUser.isNotNull());
    }

    public UserAccount getCurrentUser() { return currentUser.get(); }
    public ReadOnlyObjectProperty<UserAccount> currentUserProperty() { return currentUser; }
    public boolean isAuthenticated() { return authenticated.get(); }
    public ReadOnlyBooleanProperty authenticatedProperty() { return authenticated.getReadOnlyProperty(); }
    public String getRememberedEmail() { return rememberedEmail.get(); }
    public ReadOnlyStringProperty rememberedEmailProperty() { return rememberedEmail; }
}
