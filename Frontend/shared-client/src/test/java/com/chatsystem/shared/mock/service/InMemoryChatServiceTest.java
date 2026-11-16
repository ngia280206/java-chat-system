package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.*;
import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryChatServiceTest {
    private MockDataStore store;
    private InMemoryAuthService auth;
    private ChatService chat;

    @BeforeEach void setUp() {
        store = MockFixtures.createStore();
        auth = new InMemoryAuthService(store);
        chat = new InMemoryChatService(store, auth.getSession());
        login(auth, 1);
    }

    @Test void sendingUpdatesHeldMessagesPreviewOrderAndOtherMembersUnread() {
        var messages = chat.getMessages(10);
        var conversations = chat.getConversations();
        var otherAuth = new InMemoryAuthService(store);
        login(otherAuth, 2);
        ChatService other = new InMemoryChatService(store, otherAuth.getSession());
        var otherMessages = other.getMessages(10);
        Message sent = chat.sendMessage(10, " New message ").value().orElseThrow();
        assertEquals(4, messages.size());
        assertSame(sent, messages.get(3));
        assertSame(sent, otherMessages.get(3));
        assertEquals("New message", sent.getContent());
        assertEquals(4, sent.getSequence());
        assertEquals(1, sent.getSenderId());
        assertEquals(10, conversations.get(0).getId());
        assertEquals(sent.getContent(), conversations.get(0).getLastMessagePreview());
        assertEquals(sent.getSentAt(), conversations.get(0).getLastMessageAt());
        assertEquals(0, member(10, 1).getUnreadCount());
        assertEquals(1, member(10, 2).getUnreadCount());
        assertEquals(1, other.getUnreadCount());
        assertTrue(store.getActivityEvents().stream().anyMatch(e -> e.type() == ActivityType.MESSAGE_SENT
                && Long.valueOf(10).equals(e.conversationId()) && e.occurredAt().equals(sent.getSentAt())));
    }

    @Test void selectingMarksReadAndSelectionTracksSessionAndMembership() {
        assertEquals(4, chat.getUnreadCount());
        assertTrue(chat.selectConversation(10).isSuccess());
        assertEquals(10, chat.getSelectedConversation().getId());
        assertEquals(3, chat.getUnreadCount());
        assertEquals(0, member(10, 1).getUnreadCount());
        assertFalse(chat.selectConversation(21).isSuccess());
        assertEquals(10, chat.getSelectedConversation().getId());
        member(10, 1).setLeftAt(MockFixtures.REFERENCE_TIME);
        assertNull(chat.getSelectedConversation());
        assertTrue(chat.getMessages(10).isEmpty());
        auth.logout();
        assertEquals(0, chat.getUnreadCount());
        assertTrue(chat.getConversations().isEmpty());
    }

    @Test void hidingIsPerUserIdempotentAndSearchUsesVisibility() {
        var held = chat.getMessages(10);
        ChatService duplicate = new InMemoryChatService(store, auth.getSession());
        var duplicateHeld = duplicate.getMessages(10);
        assertEquals(List.of(1001L), ids(chat.searchMessages(10, " DESIGN ")));
        assertTrue(chat.hideMessage(1001).isSuccess());
        assertTrue(chat.hideMessage(1001).isSuccess());
        assertEquals(2, held.size());
        assertEquals(2, duplicateHeld.size());
        assertTrue(chat.searchMessages(10, "design").isEmpty());
        assertEquals(1, store.getHiddenMessages().size());
        assertEquals(12, store.getMessages().size());
        login(auth, 2);
        assertEquals(3, held.size());
        assertEquals(List.of(1001L), ids(chat.searchMessages(10, "design")));
    }

    @Test void clearUsesSequenceBoundaryAcrossInstancesAndKeepsFutureMessages() {
        var held = chat.getMessages(10);
        ChatService duplicate = new InMemoryChatService(store, auth.getSession());
        var duplicateHeld = duplicate.getMessages(10);
        assertTrue(chat.clearHistory(10).isSuccess());
        assertEquals(3, member(10, 1).getHistoryClearedThroughSeq());
        assertTrue(held.isEmpty());
        assertTrue(duplicateHeld.isEmpty());
        assertTrue(chat.searchMessages(10, "").isEmpty());
        assertEquals(12, store.getMessages().size());
        login(auth, 2);
        assertEquals(3, held.size());
        Message future = chat.sendMessage(10, "After clearing").value().orElseThrow();
        login(auth, 1);
        assertEquals(List.of(future.getId()), ids(held));
        assertEquals(1, member(10, 1).getUnreadCount());
        assertTrue(chat.clearHistory(10).isSuccess());
        assertEquals(4, member(10, 1).getHistoryClearedThroughSeq());
        assertEquals(0, member(10, 1).getUnreadCount());
    }

    @Test void directPairReusesConversationInEitherDirectionAndNewPairsAreUnique() {
        assertEquals(10, chat.startConversation(2).value().orElseThrow().getId());
        assertEquals(5, store.getConversations().size());
        Conversation created = chat.startConversation(7).value().orElseThrow();
        assertEquals(ConversationType.DIRECT, created.getType());
        assertEquals(2, store.getMembers().stream().filter(m -> m.getConversationId() == created.getId()).count());
        assertSame(created, chat.startConversation(7).value().orElseThrow());
        login(auth, 7);
        assertSame(created, chat.startConversation(1).value().orElseThrow());
        assertEquals(6, store.getConversations().size());
    }

    @Test void invalidTargetsBlockedPairsAndInactiveOrMismatchedSessionsCannotMutate() {
        int count = store.getMessages().size();
        assertFalse(chat.sendMessage(10, " ").isSuccess());
        assertFalse(chat.sendMessage(10, null).isSuccess());
        for (long id : new long[]{21, 999}) {
            assertFalse(chat.sendMessage(id, "Denied").isSuccess());
            assertFalse(chat.clearHistory(id).isSuccess());
            assertTrue(chat.getMessages(id).isEmpty());
        }
        assertFalse(chat.hideMessage(2101).isSuccess());
        assertFalse(chat.hideMessage(999).isSuccess());
        for (long id : new long[]{1, 3, 999}) assertFalse(chat.startConversation(id).isSuccess());
        assertFalse(chat.sendMessage(11, "Blocked").isSuccess());
        new InMemorySocialService(store, auth.getSession()).blockUser(2);
        assertFalse(chat.startConversation(2).isSuccess());
        assertFalse(chat.sendMessage(10, "Blocked").isSuccess());
        assertEquals(count, store.getMessages().size());
        user(1).setStatus(UserStatus.LOCKED);
        assertTrue(chat.getConversations().isEmpty());
        assertFalse(chat.sendMessage(20, "Denied").isSuccess());
        assertFalse(chat.hideMessage(1001).isSuccess());
        assertFalse(chat.clearHistory(10).isSuccess());
        user(1).setStatus(UserStatus.ACTIVE);
        ChatService mismatched = new InMemoryChatService(MockFixtures.createStore(), auth.getSession());
        assertTrue(mismatched.getConversations().isEmpty());
        assertFalse(mismatched.startConversation(2).isSuccess());
        auth.logout();
        assertNull(chat.getSelectedConversation());
        assertFalse(chat.startConversation(7).isSuccess());
        assertFalse(chat.selectConversation(10).isSuccess());
    }

    @Test void projectionsAreReadOnlyAndRefreshOnGroupDeparture() {
        assertThrows(UnsupportedOperationException.class, chat.getConversations()::clear);
        var messages = chat.getMessages(20);
        assertThrows(UnsupportedOperationException.class, messages::clear);
        assertTrue(chat.selectConversation(20).isSuccess());
        member(20, 1).setLeftAt(MockFixtures.REFERENCE_TIME);
        assertTrue(messages.isEmpty());
        assertNull(chat.getSelectedConversation());
        assertFalse(chat.sendMessage(20, "Denied").isSuccess());
    }

    @Test void reverseDirectionBlockPreventsSendingAndStartingAnExistingPair() {
        var otherAuth = new InMemoryAuthService(store);
        login(otherAuth, 2);
        assertTrue(new InMemorySocialService(store, otherAuth.getSession()).blockUser(1).isSuccess());
        assertFalse(chat.startConversation(2).isSuccess());
        assertFalse(chat.sendMessage(10, "Blocked from the other side").isSuccess());
        assertEquals(3, chat.getMessages(10).size());
        assertEquals(12, store.getMessages().size());
    }

    private UserAccount user(long id) { return store.getUsers().stream().filter(u -> u.getId() == id).findFirst().orElseThrow(); }
    private ConversationMember member(long c, long u) { return store.getMembers().stream().filter(m -> m.getConversationId() == c && m.getUserId() == u).findFirst().orElseThrow(); }
    private void login(InMemoryAuthService service, long id) { assertTrue(service.login(user(id).getEmail(), MockFixtures.DEMO_PASSWORD, false).isSuccess()); }
    private static List<Long> ids(List<Message> messages) { return messages.stream().map(Message::getId).toList(); }
}
