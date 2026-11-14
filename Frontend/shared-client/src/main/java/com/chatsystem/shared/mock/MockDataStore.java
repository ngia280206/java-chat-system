package com.chatsystem.shared.mock;

import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.UserStatus;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

    /** Mock-only credential comparison. Plaintext credentials never leave this store. */
    public boolean matchesMockPassword(long userId, String candidate) {
        return candidate != null && candidate.equals(passwords.get(userId));
    }

    /** Mock-only replacement after service validation; nothing is persisted. */
    public void replaceMockPassword(long userId, String password) {
        Objects.requireNonNull(password);
        if (!passwords.containsKey(userId)) throw new IllegalArgumentException("Unknown mock account");
        passwords.put(userId, password);
    }

    /** Adds a validated mock registration with its default settings and private credential. */
    public UserAccount createMockAccount(String displayName, String email, String password) {
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(email);
        Objects.requireNonNull(password);
        if (users.stream().anyMatch(u -> u.getEmail().equalsIgnoreCase(email))) {
            throw new IllegalArgumentException("Email is already registered");
        }
        long id = users.stream().mapToLong(UserAccount::getId).max().orElse(0) + 1;
        String username = "user" + id;
        while (usernameExists(username)) username += "_";
        UserAccount user = new UserAccount(id, email, displayName, username, "", UserStatus.ACTIVE, Instant.now());
        passwords.put(id, password);
        settings.add(new UserSettings(id));
        users.add(user);
        return user;
    }

    private boolean usernameExists(String username) {
        return users.stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(username));
    }

    /** Records successful desktop demo authentication, never a failed attempt. */
    public void recordMockLogin(long userId) {
        if (users.stream().noneMatch(u -> u.getId() == userId)) throw new IllegalArgumentException("Unknown mock account");
        long id = loginEvents.stream().mapToLong(LoginEvent::id).max().orElse(0) + 1;
        loginEvents.add(new LoginEvent(id, userId, Instant.now(), "Desktop demo", "Local demo"));
    }

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
