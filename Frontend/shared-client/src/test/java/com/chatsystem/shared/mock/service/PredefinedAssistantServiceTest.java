package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.*;
import com.chatsystem.shared.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PredefinedAssistantServiceTest {
    private InMemoryAuthService auth;
    private ChatService chat;
    private AssistantService assistant;

    @BeforeEach void setUp() {
        var store = MockFixtures.createStore();
        auth = new InMemoryAuthService(store);
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        chat = new InMemoryChatService(store, auth.getSession());
        assistant = new PredefinedAssistantService(chat);
    }

    @Test void summaryUsesOnlyVisibleContextAndDoesNotMutateChat() {
        String summary = assistant.reply(10, "Summarize this conversation").value().orElseThrow();
        assertTrue(summary.contains("design review"));
        assertTrue(summary.contains("see you at two"));
        assertEquals(3, chat.getMessages(10).size());
        chat.hideMessage(1001);
        String afterHide = assistant.reply(10, "summary").value().orElseThrow();
        assertFalse(afterHide.contains("design review"));
        chat.clearHistory(10);
        String empty = assistant.reply(10, "summary").value().orElseThrow();
        assertFalse(empty.contains("Java mockups"));
        assertTrue(empty.toLowerCase().contains("no visible messages"));
    }

    @Test void predefinedHelpAndFallbackAreDeterministicAndContextual() {
        String help = assistant.reply(10, "help me search").value().orElseThrow();
        assertTrue(help.toLowerCase().contains("search"));
        String first = assistant.reply(20, "What should we do next?").value().orElseThrow();
        assertEquals(first, assistant.reply(20, "What should we do next?").value().orElseThrow());
        assertTrue(first.contains("Design Circle"));
        assertNotEquals(first, assistant.reply(10, "What should we do next?").value().orElseThrow());
        assertEquals(3, chat.getMessages(20).size());
    }

    @Test void blankUnknownAndInaccessibleConversationPromptsAreRejected() {
        assertFalse(assistant.reply(10, " ").isSuccess());
        assertFalse(assistant.reply(10, null).isSuccess());
        assertFalse(assistant.reply(21, "summary").isSuccess());
        assertFalse(assistant.reply(999, "summary").isSuccess());
        auth.logout();
        assertFalse(assistant.reply(10, "summary").isSuccess());
    }
}
