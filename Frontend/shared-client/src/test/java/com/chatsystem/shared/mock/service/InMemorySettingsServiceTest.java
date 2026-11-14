package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.UserSettings;
import com.chatsystem.shared.model.status.UserStatus;
import com.chatsystem.shared.service.AuthService;
import com.chatsystem.shared.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemorySettingsServiceTest {
    private MockDataStore store;
    private AuthService auth;
    private SettingsService settings;

    @BeforeEach
    void setUp() {
        store = MockFixtures.createStore();
        auth = new InMemoryAuthService(store);
        settings = new InMemorySettingsService(store, auth.getSession());
    }

    @Test
    void savedSettingsNotifyObserversAndSurviveServiceRecreationAndRelogin() {
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        UserSettings displayed = settings.getSettings().value().orElseThrow();
        List<Boolean> changes = new ArrayList<>();
        displayed.soundEnabledProperty().addListener((o, oldValue, value) -> changes.add(value));
        var result = settings.updateSettings(false, true, false);
        assertTrue(result.isSuccess());
        assertSame(displayed, result.value().orElseThrow());
        assertEquals(List.of(true), changes);
        auth.logout();
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        UserSettings restored = new InMemorySettingsService(store, auth.getSession()).getSettings().value().orElseThrow();
        assertSame(displayed, restored);
        assertFalse(restored.isDesktopNotifications());
        assertTrue(restored.isSoundEnabled());
        assertFalse(restored.isMessagePreviews());
        UserSettings independent = MockFixtures.createStore().getSettings().get(0);
        assertTrue(independent.isDesktopNotifications());
        assertFalse(independent.isSoundEnabled());
    }

    @Test
    void preferencesBelongOnlyToAuthenticatedUser() {
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        settings.updateSettings(false, true, false);
        auth.login("mia.tran@example.com", MockFixtures.DEMO_PASSWORD, false);
        UserSettings other = settings.getSettings().value().orElseThrow();
        assertEquals(2, other.getUserId());
        assertTrue(other.isDesktopNotifications());
        assertFalse(other.isSoundEnabled());
        assertTrue(other.isMessagePreviews());
    }

    @Test
    void unauthenticatedAndLockedSessionsCannotReadOrSaveSettings() {
        assertFalse(settings.getSettings().isSuccess());
        assertFalse(settings.updateSettings(false, true, false).isSuccess());
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        store.getUsers().get(0).setStatus(UserStatus.LOCKED);
        assertFalse(settings.getSettings().isSuccess());
        assertFalse(settings.updateSettings(false, true, false).isSuccess());
        assertTrue(store.getSettings().get(0).isDesktopNotifications());
        assertFalse(store.getSettings().get(0).isSoundEnabled());
    }

    @Test
    void abandoningLocalPreferenceDraftWithoutUpdateLeavesSavedValues() {
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        UserSettings original = settings.getSettings().value().orElseThrow();
        boolean draftSound = !original.isSoundEnabled();
        assertNotEquals(draftSound, settings.getSettings().value().orElseThrow().isSoundEnabled());
        assertFalse(store.getSettings().get(0).isSoundEnabled());
    }
}
