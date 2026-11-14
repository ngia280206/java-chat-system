package com.chatsystem.shared.mock;

import com.chatsystem.shared.model.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Process-local demo data. Backing state is package-private so fixtures and later
 * focused service operations own mutations. Applications receive read-only views.
 * Passwords are deliberately mock-only and never exposed to UI consumers.
 */
public final class MockDataStore {
    public record Friendship(long firstUserId, long secondUserId, Instant createdAt) {
        public Friendship {
            if (firstUserId >= secondUserId) throw new IllegalArgumentException("Friendship IDs must be ordered and distinct");
            java.util.Objects.requireNonNull(createdAt);
        }
    }

    public record Block(long blockerId, long blockedId, Instant createdAt) {
        public Block {
            if (blockerId == blockedId) throw new IllegalArgumentException("Cannot block yourself");
            java.util.Objects.requireNonNull(createdAt);
        }
    }

    public record HiddenMessage(long userId, long messageId, Instant hiddenAt) {
        public HiddenMessage { java.util.Objects.requireNonNull(hiddenAt); }
    }

    final ObservableList<UserAccount> users = FXCollections.observableArrayList();
    final ObservableList<UserSettings> settings = FXCollections.observableArrayList();
    final ObservableList<FriendRequest> friendRequests = FXCollections.observableArrayList();
    final ObservableList<Friendship> friendships = FXCollections.observableArrayList();
    final ObservableList<Block> blocks = FXCollections.observableArrayList();
    final ObservableList<Conversation> conversations = FXCollections.observableArrayList();
    final ObservableList<ConversationMember> members = FXCollections.observableArrayList();
    final ObservableList<Message> messages = FXCollections.observableArrayList();
    final ObservableList<GroupInvitation> invitations = FXCollections.observableArrayList();
    final ObservableList<SpamReport> reports = FXCollections.observableArrayList();
    final ObservableList<LoginEvent> loginEvents = FXCollections.observableArrayList();
    final ObservableList<ActivityEvent> activityEvents = FXCollections.observableArrayList();
    final ObservableList<HiddenMessage> hiddenMessages = FXCollections.observableArrayList();
    final Map<Long, String> passwords = new HashMap<>();

    private final ObservableList<UserAccount> usersView = FXCollections.unmodifiableObservableList(users);
    private final ObservableList<UserSettings> settingsView = FXCollections.unmodifiableObservableList(settings);
    private final ObservableList<FriendRequest> requestsView = FXCollections.unmodifiableObservableList(friendRequests);
    private final ObservableList<Friendship> friendshipsView = FXCollections.unmodifiableObservableList(friendships);
    private final ObservableList<Block> blocksView = FXCollections.unmodifiableObservableList(blocks);
    private final ObservableList<Conversation> conversationsView = FXCollections.unmodifiableObservableList(conversations);
    private final ObservableList<ConversationMember> membersView = FXCollections.unmodifiableObservableList(members);
    private final ObservableList<Message> messagesView = FXCollections.unmodifiableObservableList(messages);
    private final ObservableList<GroupInvitation> invitationsView = FXCollections.unmodifiableObservableList(invitations);
    private final ObservableList<SpamReport> reportsView = FXCollections.unmodifiableObservableList(reports);
    private final ObservableList<LoginEvent> loginEventsView = FXCollections.unmodifiableObservableList(loginEvents);
    private final ObservableList<ActivityEvent> activityEventsView = FXCollections.unmodifiableObservableList(activityEvents);
    private final ObservableList<HiddenMessage> hiddenMessagesView = FXCollections.unmodifiableObservableList(hiddenMessages);

    MockDataStore() { }

    public ObservableList<UserAccount> getUsers() { return usersView; }
    public ObservableList<UserSettings> getSettings() { return settingsView; }
    public ObservableList<FriendRequest> getFriendRequests() { return requestsView; }
    public ObservableList<Friendship> getFriendships() { return friendshipsView; }
    public ObservableList<Block> getBlocks() { return blocksView; }
    public ObservableList<Conversation> getConversations() { return conversationsView; }
    public ObservableList<ConversationMember> getMembers() { return membersView; }
    public ObservableList<Message> getMessages() { return messagesView; }
    public ObservableList<GroupInvitation> getInvitations() { return invitationsView; }
    public ObservableList<SpamReport> getReports() { return reportsView; }
    public ObservableList<LoginEvent> getLoginEvents() { return loginEventsView; }
    public ObservableList<ActivityEvent> getActivityEvents() { return activityEventsView; }
    public ObservableList<HiddenMessage> getHiddenMessages() { return hiddenMessagesView; }
}
