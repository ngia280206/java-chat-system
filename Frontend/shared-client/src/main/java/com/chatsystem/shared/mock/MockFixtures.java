package com.chatsystem.shared.mock;

import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Stable, relational demo state. Every call creates a fully independent
 * session.
 */
public final class MockFixtures {

    public static final long DEMO_USER_ID = 1L;
    public static final long LOCKED_USER_ID = 3L;
    public static final long DIRECT_CONVERSATION_ID = 10L;
    public static final long GROUP_CONVERSATION_ID = 20L;
    public static final long INVITED_GROUP_ID = 21L;
    /**
     * Mock-only login: alex.morgan@example.com / DemoPass123!
     */
    public static final String DEMO_EMAIL = "alex.morgan@example.com";
    public static final String DEMO_PASSWORD = "DemoPass123!";
    public static final String LOCKED_EMAIL = "jordan.lee@example.com";
    /**
     * All seeded users share the demo password; registration/reset remain in
     * memory.
     */
    public static final String RESET_CODE = "123456";
    /**
     * Fixed reference time keeps history, filtering, and metrics reproducible.
     */
    public static final Instant REFERENCE_TIME = Instant.parse("2026-11-14T05:00:00Z");

    private MockFixtures() {
    }

    public static MockDataStore createStore() {
        MockDataStore store = new MockDataStore();
        seedUsers(store);
        seedConversations(store);
        seedRelationships(store);
        seedReportsAndHistory(store);
        return store;
    }

    private static void seedUsers(MockDataStore store) {
        String[] names = {"Alex Morgan", "Mia Tran", "Jordan Lee", "Noah Wilson", "Casey Brown", "Emma Davis",
            "Liam Nguyen", "Olivia Chen", "Ethan Miller", "Ava Patel", "Lucas Garcia", "Sophia Kim",
            "Oliver Martin", "Amelia Clark", "James Hall", "Charlotte Allen", "Benjamin Young", "Isabella King",
            "Henry Wright", "Harper Scott", "Daniel Green", "Evelyn Adams", "Samuel Baker", "Aria Nelson"};
        for (int i = 0; i < names.length; i++) {
            long id = i + 1L;
            String email = names[i].toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.com";
            String username = names[i].toLowerCase(Locale.ROOT).replace(" ", "");
            store.users.add(new UserAccount(id, email, names[i], username,
                    id == DEMO_USER_ID ? "Design enthusiast and Java developer" : "Building things together",
                    id == LOCKED_USER_ID || id == 18 ? UserStatus.LOCKED : UserStatus.ACTIVE,
                    REFERENCE_TIME.minus(250 - i * 10L, ChronoUnit.DAYS)));
            UserAccount account = store.users.get(store.users.size() - 1);
            account.updatePersonalDetails(i % 3 == 0 ? "12 Example Street, Ho Chi Minh City"
                    : i % 3 == 1 ? "88 Riverside Road" : "", java.time.LocalDate.of(2000 + (i % 6), i % 12 + 1, i % 27 + 1),
                    i % 3 == 0 ? "Male" : i % 3 == 1 ? "Female" : "Other");
            account.setOnline(id != LOCKED_USER_ID && id != 18 && i % 3 != 1);
            store.settings.add(new UserSettings(id));
            store.passwords.put(id, DEMO_PASSWORD);
        }
    }

    private static void seedConversations(MockDataStore store) {
        addConversation(store, 10, ConversationType.DIRECT, 1, "", "", new long[]{1, 2},
                new long[]{2, 1, 2}, new String[]{"Hi Alex, ready for the design review?", "Yes, I have the Java mockups ready.", "Great, see you at two!"});
        addConversation(store, 11, ConversationType.DIRECT, 1, "", "", new long[]{1, 3},
                new long[]{3, 1}, new String[]{"Try this promotional offer today", "Please stop sending promotional links."});
        addConversation(store, 20, ConversationType.GROUP, 1, "Design Circle", "Exchange ideas, feedback, and design resources.",
                new long[]{1, 2, 4, 5}, new long[]{2, 4, 1},
                new String[]{"Welcome to Design Circle!", "The new color palette is ready.", "Let's review the palette together."});
        addConversation(store, 21, ConversationType.GROUP, 2, "Java Study Group", "Practice Java and discuss the course project.",
                new long[]{2, 5, 6}, new long[]{2, 5}, new String[]{"Our Java session starts tomorrow.", "I'll bring the examples."});
        addConversation(store, 22, ConversationType.GROUP, 4, "Weekend Plans", "Share local events and weekend ideas.",
                new long[]{4, 1}, new long[]{4, 1}, new String[]{"Coffee this Saturday?", "Count me in!"});
    }

    private static void addConversation(MockDataStore store, long id, ConversationType type, long creator,
            String name, String description, long[] users, long[] senders, String[] contents) {
        Instant created = REFERENCE_TIME.minus(40 + id, ChronoUnit.DAYS);
        Conversation conversation = new Conversation(id, type, creator, created, name, description);
        store.conversations.add(conversation);
        for (long user : users) {
            ConversationMember member = new ConversationMember(id, user,
                    type == ConversationType.GROUP && user == creator ? GroupRole.ADMIN : GroupRole.MEMBER, created);
            if (user == DEMO_USER_ID) {
                member.setUnreadCount(1);
            }
            store.members.add(member);
        }
        for (int i = 0; i < contents.length; i++) {
            Instant sent = REFERENCE_TIME.minus(2, ChronoUnit.DAYS).plus(id * 60 + i * 300, ChronoUnit.SECONDS);
            Message message = new Message(id * 100 + i + 1, id, senders[i], i + 1L, contents[i], sent);
            store.messages.add(message);
            conversation.updatePreview(message.getContent(), message.getSentAt());
        }
    }

    private static void seedRelationships(MockDataStore store) {
        Instant created = REFERENCE_TIME.minus(20, ChronoUnit.DAYS);
        store.friendships.addAll(new MockDataStore.Friendship(1, 2, created.plusSeconds(1800)), new MockDataStore.Friendship(1, 4, created),
                new MockDataStore.Friendship(1, 5, created));
        store.blocks.add(new MockDataStore.Block(1, 3, REFERENCE_TIME.minus(1, ChronoUnit.DAYS)));
        addRequest(store, 301, 6, 1, FriendRequestStatus.PENDING, created);
        addRequest(store, 302, 1, 7, FriendRequestStatus.PENDING, created);
        addRequest(store, 303, 2, 1, FriendRequestStatus.ACCEPTED, created);
        addRequest(store, 304, 8, 1, FriendRequestStatus.REJECTED, created);
        addRequest(store, 305, 1, 9, FriendRequestStatus.CANCELLED, created);
        GroupInvitation pending = new GroupInvitation(401, 21, 2, 1, REFERENCE_TIME.minus(1, ChronoUnit.DAYS));
        GroupInvitation accepted = new GroupInvitation(402, 20, 1, 5, created);
        accepted.respond(FriendRequestStatus.ACCEPTED, created.plusSeconds(3600));
        // Casey joined after accepting this invitation; retain the original membership identity.
        store.members.stream().filter(m -> m.getConversationId() == 20 && m.getUserId() == 5).findFirst()
                .ifPresent(member -> {
                    store.members.remove(member);
                    store.members.add(new ConversationMember(20, 5, GroupRole.MEMBER, created.plusSeconds(3600)));
                });
        GroupInvitation rejected = new GroupInvitation(403, 21, 2, 7, created);
        rejected.respond(FriendRequestStatus.REJECTED, created.plusSeconds(7200));
        store.invitations.addAll(pending, accepted, rejected);
    }

    private static void addRequest(MockDataStore store, long id, long sender, long receiver, FriendRequestStatus status, Instant created) {
        FriendRequest request = new FriendRequest(id, sender, receiver, created);
        if (status != FriendRequestStatus.PENDING) {
            request.respond(status, created.plusSeconds(1800));
        }
        store.friendRequests.add(request);
    }

    private static void seedReportsAndHistory(MockDataStore store) {
        Instant reported = REFERENCE_TIME.minus(1, ChronoUnit.DAYS);
        SpamReport pending = new SpamReport(501, 1, 3, 1101, "Unwanted promotional message", reported);
        SpamReport reviewed = new SpamReport(502, 1, 2, 1001, "Repeated invitation", reported);
        reviewed.review(ReportStatus.REVIEWED, "Reviewed context and sent a reminder; account remains active.");
        SpamReport dismissed = new SpamReport(503, 4, 2, 2001, "Suspected spam", reported);
        dismissed.review(ReportStatus.DISMISSED, "Welcome message is relevant to the group.");
        store.reports.addAll(pending, reviewed, dismissed);
        for (int i = 0; i < 35; i++) {
            long user = i % 6 + 1L;
            Instant time = REFERENCE_TIME.minus(i, ChronoUnit.DAYS).minus(i * 7L, ChronoUnit.MINUTES);
            store.loginEvents.add(new LoginEvent(2001 + i, user, time,
                    i % 2 == 0 ? "Windows desktop" : "Android phone", i % 3 == 0 ? "Da Nang" : "Ho Chi Minh City"));
            store.activityEvents.add(new ActivityEvent(4001 + i, user, ActivityType.APP_OPEN, null, time));
        }
        for (Message message : store.messages) {
            store.activityEvents.add(new ActivityEvent(5000 + message.getId(), message.getSenderId(), ActivityType.MESSAGE_SENT,
                    message.getConversationId(), message.getSentAt()));
        }
    }
}
