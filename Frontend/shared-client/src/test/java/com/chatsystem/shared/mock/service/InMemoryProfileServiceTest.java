package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.UserStatus;
import com.chatsystem.shared.model.status.ProfileVisibility;
import com.chatsystem.shared.service.AuthService;
import com.chatsystem.shared.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryProfileServiceTest {
    private MockDataStore store;
    private AuthService auth;
    private ProfileService profiles;

    @BeforeEach
    void setUp() {
        store = MockFixtures.createStore();
        auth = new InMemoryAuthService(store);
        profiles = new InMemoryProfileService(store, auth.getSession());
    }

    @Test
    void profileSavePropagatesToExistingIdentityAndObservers() {
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        UserAccount displayed = store.getUsers().get(0);
        List<String> changes = new ArrayList<>();
        displayed.displayNameProperty().addListener((o, oldValue, value) -> changes.add(value));
        var result = profiles.updateProfile(" New Name ", " new_name ", "New biography", ProfileVisibility.EVERYONE);
        assertTrue(result.isSuccess());
        assertSame(displayed, result.value().orElseThrow());
        assertSame(displayed, auth.getSession().getCurrentUser());
        assertEquals(List.of("New Name"), changes);
        assertEquals("new_name", displayed.getUsername());
        assertEquals("New biography", displayed.getBio());
        assertEquals(ProfileVisibility.EVERYONE, displayed.getProfileVisibility());
        assertEquals(MockFixtures.DEMO_EMAIL, displayed.getEmail());
        assertEquals("Mia Tran", store.getUsers().get(1).getDisplayName());
    }

    @Test
    void profileValidationRejectsInvalidAndDuplicateFieldsWithoutPartialMutation() {
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        var result = profiles.updateProfile(" ", "bad name!", "x".repeat(501), ProfileVisibility.EVERYONE);
        assertFalse(result.isSuccess());
        assertEquals(List.of("displayName", "username", "bio"), result.errors().stream().map(e -> e.field()).toList());
        assertFalse(profiles.updateProfile("Changed", " MIATRAN ", "Changed", ProfileVisibility.EVERYONE).isSuccess());
        assertEquals("Alex Morgan", store.getUsers().get(0).getDisplayName());
        assertEquals("alexmorgan", store.getUsers().get(0).getUsername());
        assertEquals("Design enthusiast and Java developer", store.getUsers().get(0).getBio());
        assertEquals(ProfileVisibility.FRIENDS_ONLY, store.getUsers().get(0).getProfileVisibility());
        assertTrue(profiles.updateProfile("Alex", "alexmorgan", "", ProfileVisibility.FRIENDS_ONLY).isSuccess());
    }

    @Test
    void unauthenticatedOrLockedSessionCannotSaveProfile() {
        assertFalse(profiles.updateProfile("Changed", "changed", "", ProfileVisibility.EVERYONE).isSuccess());
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        store.getUsers().get(0).setStatus(UserStatus.LOCKED);
        assertFalse(profiles.updateProfile("Changed", "changed", "", ProfileVisibility.EVERYONE).isSuccess());
        assertEquals("Alex Morgan", store.getUsers().get(0).getDisplayName());
    }

    @Test
    void discardingLocalProfileDraftWithoutSaveDoesNotMutateSharedIdentity() {
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        String draftName = "Unsaved Name";
        assertNotEquals(draftName, auth.getSession().getCurrentUser().getDisplayName());
        assertEquals("Alex Morgan", store.getUsers().get(0).getDisplayName());
    }

    @Test
    void visibilityPreferenceIsObservablePerUserAndInvalidChoiceDoesNotSaveOtherEdits() {
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        UserAccount user = auth.getSession().getCurrentUser();
        List<ProfileVisibility> changes = new ArrayList<>();
        user.profileVisibilityProperty().addListener((o, oldValue, value) -> changes.add(value));
        assertFalse(user.profileVisibilityProperty() instanceof javafx.beans.property.Property<?>);
        var invalid = profiles.updateProfile("Changed", "changed", "", null);
        assertFalse(invalid.isSuccess());
        assertEquals("profileVisibility", invalid.errors().get(0).field());
        assertEquals("Alex Morgan", user.getDisplayName());
        assertTrue(profiles.updateProfile("Alex Morgan", "alexmorgan", "", ProfileVisibility.EVERYONE).isSuccess());
        assertEquals(List.of(ProfileVisibility.EVERYONE), changes);
        auth.logout();
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        assertEquals(ProfileVisibility.EVERYONE, auth.getSession().getCurrentUser().getProfileVisibility());
        assertEquals(ProfileVisibility.FRIENDS_ONLY, store.getUsers().get(1).getProfileVisibility());
        assertEquals(ProfileVisibility.FRIENDS_ONLY, MockFixtures.createStore().getUsers().get(0).getProfileVisibility());
    }
}
