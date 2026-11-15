package com.chatsystem.shared.mock;

import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.UserStatus;
import com.chatsystem.shared.model.status.ConversationType;
import com.chatsystem.shared.model.status.GroupRole;
import javafx.beans.Observable;
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

    final ObservableList<UserAccount> users = FXCollections.observableArrayList(
            user -> new Observable[]{user.statusProperty()});
    final ObservableList<UserSettings> settings = FXCollections.observableArrayList();
    final ObservableList<FriendRequest> friendRequests = FXCollections.observableArrayList(
            request -> new Observable[]{request.statusProperty()});
    final ObservableList<Friendship> friendships = FXCollections.observableArrayList();
    final ObservableList<Block> blocks = FXCollections.observableArrayList();
    final ObservableList<Conversation> conversations = FXCollections.observableArrayList();
    final ObservableList<ConversationMember> members = FXCollections.observableArrayList(
            member -> new Observable[]{member.roleProperty(), member.leftAtProperty()});
    final ObservableList<Message> messages = FXCollections.observableArrayList();
    final ObservableList<GroupInvitation> invitations = FXCollections.observableArrayList(
            invitation -> new Observable[]{invitation.statusProperty()});
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

    // Narrow storage operations for validated service commands. No mutable lists escape.
    public FriendRequest createMockFriendRequest(long senderId, long receiverId) {
        long id = friendRequests.stream().mapToLong(FriendRequest::getId).max().orElse(0) + 1;
        FriendRequest request = new FriendRequest(id, senderId, receiverId, Instant.now());
        friendRequests.add(request);
        return request;
    }

    public void addMockFriendship(long firstId, long secondId) {
        friendships.add(new Friendship(Math.min(firstId, secondId), Math.max(firstId, secondId), Instant.now()));
    }

    public void removeMockFriendship(long firstId, long secondId) {
        friendships.removeIf(f -> f.firstUserId() == Math.min(firstId, secondId)
                && f.secondUserId() == Math.max(firstId, secondId));
    }

    public void addMockBlock(long blockerId, long blockedId) {
        blocks.add(new Block(blockerId, blockedId, Instant.now()));
    }

    public void removeMockBlock(long blockerId, long blockedId) {
        blocks.removeIf(b -> b.blockerId() == blockerId && b.blockedId() == blockedId);
    }

    public Conversation createMockGroup(long creatorId, String name, String description) {
        long id = conversations.stream().mapToLong(Conversation::getId).max().orElse(0) + 1;
        Instant now = Instant.now();
        Conversation group = new Conversation(id, ConversationType.GROUP, creatorId, now, name, description);
        members.add(new ConversationMember(id, creatorId, GroupRole.ADMIN, now));
        conversations.add(group);
        return group;
    }

    public GroupInvitation createMockGroupInvitation(long groupId, long inviterId, long inviteeId) {
        long id = invitations.stream().mapToLong(GroupInvitation::getId).max().orElse(0) + 1;
        GroupInvitation invitation = new GroupInvitation(id, groupId, inviterId, inviteeId, Instant.now());
        invitations.add(invitation);
        return invitation;
    }

    public void addMockGroupMember(long groupId, long userId) {
        members.add(new ConversationMember(groupId, userId, GroupRole.MEMBER, Instant.now()));
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
