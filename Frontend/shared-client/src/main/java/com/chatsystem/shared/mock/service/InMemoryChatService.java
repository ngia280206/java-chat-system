package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.service.ChatService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.shared.validation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.*;

public final class InMemoryChatService implements ChatService {
    private final MockDataStore store;
    private final UserSession session;
    private final ObservableList<Conversation> conversations = FXCollections.observableArrayList();
    private final ObservableList<Conversation> conversationsView = FXCollections.unmodifiableObservableList(conversations);
    private final Map<Long, ObservableList<Message>> messages = new HashMap<>();
    private final Map<Long, ObservableList<Message>> messageViews = new HashMap<>();
    private final ReadOnlyObjectWrapper<Conversation> selected = new ReadOnlyObjectWrapper<>(this, "selectedConversation");
    private final ReadOnlyIntegerWrapper unread = new ReadOnlyIntegerWrapper(this, "unreadCount");

    public InMemoryChatService(MockDataStore store, UserSession session) {
        this.store = Objects.requireNonNull(store);
        this.session = Objects.requireNonNull(session);
        InvalidationListener refresh = ignored -> refresh();
        session.currentUserProperty().addListener((o, previous, current) -> { selected.set(null); refresh(); });
        store.getUsers().addListener(refresh);
        store.getConversations().addListener(refresh);
        store.getMembers().addListener(refresh);
        store.getMessages().addListener(refresh);
        store.getHiddenMessages().addListener(refresh);
        refresh();
    }

    @Override public ObservableList<Conversation> getConversations() { return conversationsView; }
    @Override public Conversation getSelectedConversation() { return selected.get(); }
    @Override public ReadOnlyObjectProperty<Conversation> selectedConversationProperty() { return selected.getReadOnlyProperty(); }
    @Override public int getUnreadCount() { return unread.get(); }
    @Override public ReadOnlyIntegerProperty unreadCountProperty() { return unread.getReadOnlyProperty(); }
    @Override public boolean isUnread(long conversationId) {
        UserAccount actor = actor();
        ConversationMember member = actor == null ? null : membership(conversationId, actor.getId());
        return member != null && member.getUnreadCount() > 0;
    }

    @Override public ObservableList<Message> getMessages(long conversationId) {
        return messageViews.computeIfAbsent(conversationId, id -> {
            ObservableList<Message> list = FXCollections.observableArrayList(visibleMessages(id));
            messages.put(id, list);
            return FXCollections.unmodifiableObservableList(list);
        });
    }

    @Override public ValidationResult<Void> selectConversation(long conversationId) {
        Conversation conversation = accessibleConversation(conversationId);
        if (conversation == null) return failure("Choose a conversation you currently belong to");
        selected.set(conversation);
        membership(conversationId, actor().getId()).setUnreadCount(0);
        return ValidationResult.success(null);
    }

    @Override public ValidationResult<Conversation> startConversation(long recipientId) {
        UserAccount actor = actor();
        if (actor == null || recipientId == actor.getId() || !activeUser(recipientId) || blocked(actor.getId(), recipientId)) {
            return failure("Choose another active, unblocked account");
        }
        Conversation existing = store.getConversations().stream().filter(c -> c.getType() == ConversationType.DIRECT)
                .filter(c -> membership(c.getId(), actor.getId()) != null && membership(c.getId(), recipientId) != null)
                .filter(c -> store.getMembers().stream().filter(m -> m.getConversationId() == c.getId()).count() == 2)
                .min(Comparator.comparingLong(Conversation::getId)).orElse(null);
        return ValidationResult.success(existing != null ? existing : store.createMockDirectConversation(actor.getId(), recipientId));
    }

    @Override public ValidationResult<Message> sendMessage(long conversationId, String content) {
        Conversation conversation = accessibleConversation(conversationId);
        if (conversation == null) return failure("Choose a conversation you currently belong to");
        if (content == null || content.isBlank()) {
            return ValidationResult.failure(List.of(ValidationError.field("content", "Message text is required")));
        }
        long senderId = actor().getId();
        if (conversation.getType() == ConversationType.DIRECT && store.getMembers().stream()
                .filter(m -> m.getConversationId() == conversationId && m.getUserId() != senderId)
                .anyMatch(m -> m.getLeftAt() != null || !activeUser(m.getUserId()) || blocked(senderId, m.getUserId()))) {
            return failure("This direct conversation is unavailable while an account is inactive or blocked");
        }
        return ValidationResult.success(store.createMockMessage(conversationId, senderId, content.strip()));
    }

    @Override public List<Message> searchMessages(long conversationId, String query) {
        String text = MockServiceQueries.normalize(query);
        return visibleMessages(conversationId).stream().filter(m -> MockServiceQueries.normalize(m.getContent()).contains(text)).toList();
    }

    @Override public ValidationResult<Void> hideMessage(long messageId) {
        UserAccount actor = actor();
        Message message = store.getMessages().stream().filter(m -> m.getId() == messageId).findFirst().orElse(null);
        if (actor == null || message == null || accessibleConversation(message.getConversationId()) == null) {
            return failure("Choose a visible message");
        }
        boolean alreadyHidden = store.getHiddenMessages().stream().anyMatch(h -> h.userId() == actor.getId() && h.messageId() == messageId);
        if (!alreadyHidden && !store.isMockMessageVisible(actor.getId(), message)) return failure("Choose a visible message");
        store.hideMockMessage(actor.getId(), messageId);
        return ValidationResult.success(null);
    }

    @Override public ValidationResult<Void> clearHistory(long conversationId) {
        if (accessibleConversation(conversationId) == null) return failure("Choose a conversation you currently belong to");
        long boundary = store.getMessages().stream().filter(m -> m.getConversationId() == conversationId)
                .mapToLong(Message::getSequence).max().orElse(0);
        ConversationMember member = membership(conversationId, actor().getId());
        member.clearHistoryThrough(boundary);
        member.setUnreadCount(0);
        return ValidationResult.success(null);
    }

    private void refresh() {
        conversations.setAll(store.getConversations().stream().filter(c -> accessibleConversation(c.getId()) != null)
                .sorted(Comparator.<Conversation, java.time.Instant>comparing(c -> c.getLastMessageAt() == null
                                ? c.getCreatedAt() : c.getLastMessageAt()).reversed()
                        .thenComparing(Comparator.comparingLong(Conversation::getId).reversed())).toList());
        if (selected.get() != null && !conversations.contains(selected.get())) selected.set(null);
        UserAccount actor = actor();
        unread.set(actor == null ? 0 : store.getMembers().stream().filter(m -> m.getUserId() == actor.getId()
                && m.getLeftAt() == null && conversations.stream().anyMatch(c -> c.getId() == m.getConversationId()))
                .mapToInt(ConversationMember::getUnreadCount).sum());
        messages.forEach((id, list) -> list.setAll(visibleMessages(id)));
    }

    private List<Message> visibleMessages(long conversationId) {
        UserAccount actor = actor();
        if (actor == null || accessibleConversation(conversationId) == null) return List.of();
        return store.getMessages().stream().filter(m -> m.getConversationId() == conversationId && store.isMockMessageVisible(actor.getId(), m))
                .sorted(Comparator.comparingLong(Message::getSequence).thenComparingLong(Message::getId)).toList();
    }

    private Conversation accessibleConversation(long id) {
        UserAccount actor = actor();
        if (actor == null || membership(id, actor.getId()) == null) return null;
        return store.getConversations().stream().filter(c -> c.getId() == id).findFirst().orElse(null);
    }

    private ConversationMember membership(long conversationId, long userId) {
        return store.getMembers().stream().filter(m -> m.getConversationId() == conversationId && m.getUserId() == userId
                && m.getLeftAt() == null).findFirst().orElse(null);
    }

    private boolean blocked(long firstId, long secondId) {
        return store.getBlocks().stream().anyMatch(b -> (b.blockerId() == firstId && b.blockedId() == secondId)
                || (b.blockerId() == secondId && b.blockedId() == firstId));
    }

    private boolean activeUser(long id) {
        return store.getUsers().stream().anyMatch(u -> u.getId() == id && u.getStatus() == UserStatus.ACTIVE);
    }

    private UserAccount actor() {
        UserAccount actor = session.getCurrentUser();
        return actor != null && actor.getStatus() == UserStatus.ACTIVE && store.getUsers().contains(actor) ? actor : null;
    }

    private static <T> ValidationResult<T> failure(String message) {
        return ValidationResult.failure(List.of(ValidationError.global(message)));
    }
}
