package com.chatsystem.user;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.status.ProfileVisibility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserApplicationContextTest {
    @Test
    void freshContextsKeepAuthenticationAndSharedServiceMutationsIndependent() {
        var first = new UserApplicationContext();
        var second = new UserApplicationContext();
        assertFalse(first.session().isAuthenticated());
        assertTrue(first.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, true).isSuccess());
        assertFalse(second.session().isAuthenticated());
        assertTrue(first.profiles().updateProfile("Changed Alex", "changedalex", "Updated", ProfileVisibility.EVERYONE).isSuccess());
        assertEquals("Changed Alex", first.admin().getUser(1).orElseThrow().getDisplayName());
        assertEquals("Alex Morgan", second.admin().getUser(1).orElseThrow().getDisplayName());
        assertEquals("Changed Alex", first.session().getCurrentUser().getDisplayName());
        assertSame(first.store().getUsers().get(0), first.session().getCurrentUser());
        assertSame(first.session(), first.auth().getSession());
        assertTrue(first.settings().updateSettings(false, false, false).isSuccess());
        assertTrue(second.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false).isSuccess());
        assertTrue(second.settings().getSettings().value().orElseThrow().isDesktopNotifications());
        assertFalse(first.settings().getSettings().value().orElseThrow().isDesktopNotifications());
    }

    @Test
    void composedSocialGroupChatReportAndAssistantServicesShareOneSessionStore() {
        var context = new UserApplicationContext();
        context.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        assertFalse(context.social().getFriends().isEmpty());
        assertFalse(context.groups().getGroups().isEmpty());
        assertFalse(context.chat().getConversations().isEmpty());
        assertFalse(context.reports().getReports().isEmpty());
        assertTrue(context.assistant().reply(10, "summarize").value().orElseThrow().contains("Great, see you at two!"));
        context.auth().logout();
        assertTrue(context.chat().getConversations().isEmpty());
        assertTrue(context.groups().getGroups().isEmpty());
        assertTrue(context.social().getFriends().isEmpty());
        assertTrue(context.reports().getReports().isEmpty());
    }
}
