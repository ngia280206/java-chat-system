package com.chatsystem.shared.mock;

import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.*;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MockFixturesTest {
    @Test
    void stableDemoAccountsHaveCredentialsAndEnoughRowsForPagination() {
        MockDataStore store = MockFixtures.createStore();
        assertTrue(store.getUsers().size() > 20);
        UserAccount demo = store.getUsers().stream().filter(u -> u.getId() == MockFixtures.DEMO_USER_ID).findFirst().orElseThrow();
        assertEquals("Alex Morgan", demo.getDisplayName());
        assertEquals(MockFixtures.DEMO_EMAIL, demo.getEmail());
        assertEquals(MockFixtures.DEMO_PASSWORD, store.passwords.get(demo.getId()));
        assertEquals(UserStatus.ACTIVE, demo.getStatus());
        assertTrue(store.getUsers().stream().anyMatch(u -> u.getStatus() == UserStatus.LOCKED));
        assertTrue(store.getUsers().stream().noneMatch(u -> u.getDisplayName().contains("no-matching-user")));
        assertEquals(store.getUsers().size(), store.getSettings().size());
        assertFalse(MockFixtures.RESET_CODE.isBlank());
    }

    @Test
    void everyFixtureRelationshipResolvesToRealRecords() {
        MockDataStore store = MockFixtures.createStore();
        Set<Long> users = store.getUsers().stream().map(UserAccount::getId).collect(Collectors.toSet());
        Set<Long> conversations = store.getConversations().stream().map(Conversation::getId).collect(Collectors.toSet());
        assertEquals(store.getUsers().size(), users.size());
        assertEquals(store.getConversations().size(), conversations.size());
        assertEquals(users.size(), store.getUsers().stream().map(UserAccount::getEmail).distinct().count());
        assertEquals(users.size(), store.getUsers().stream().map(UserAccount::getUsername).distinct().count());
        Set<String> membershipKeys = new HashSet<>();
        for (ConversationMember member : store.getMembers()) {
            assertTrue(users.contains(member.getUserId()));
            assertTrue(conversations.contains(member.getConversationId()));
            assertTrue(membershipKeys.add(member.getConversationId() + ":" + member.getUserId()));
        }
        for (Conversation conversation : store.getConversations()) {
            assertTrue(users.contains(conversation.getCreatedBy()));
            var members = store.getMembers().stream().filter(m -> m.getConversationId() == conversation.getId() && m.getLeftAt() == null).toList();
            assertTrue(members.stream().anyMatch(m -> m.getUserId() == conversation.getCreatedBy()));
            if (conversation.getType() == ConversationType.DIRECT) {
                assertEquals(2, members.size());
                assertTrue(members.stream().allMatch(m -> m.getRole() == GroupRole.MEMBER));
            } else {
                assertTrue(members.size() >= 2);
                assertTrue(members.stream().anyMatch(m -> m.getRole() == GroupRole.ADMIN));
            }
            Message latest = store.getMessages().stream().filter(m -> m.getConversationId() == conversation.getId())
                    .max(java.util.Comparator.comparingLong(Message::getSequence)).orElseThrow();
            assertEquals(latest.getContent(), conversation.getLastMessagePreview());
            assertEquals(latest.getSentAt(), conversation.getLastMessageAt());
        }
        Set<Long> messageIds = new HashSet<>();
        Set<String> sequences = new HashSet<>();
        for (Message message : store.getMessages()) {
            assertTrue(messageIds.add(message.getId()));
            assertTrue(sequences.add(message.getConversationId() + ":" + message.getSequence()));
            assertTrue(message.getSequence() > 0);
            assertTrue(membershipKeys.contains(message.getConversationId() + ":" + message.getSenderId()));
            assertFalse(message.getContent().isBlank());
        }
        for (FriendRequest request : store.getFriendRequests()) {
            assertTrue(users.contains(request.getSenderId()));
            assertTrue(users.contains(request.getReceiverId()));
            assertNotEquals(request.getSenderId(), request.getReceiverId());
            assertEquals(request.getStatus() == FriendRequestStatus.PENDING, request.getRespondedAt() == null);
        }
        for (var friendship : store.getFriendships()) {
            assertTrue(users.contains(friendship.firstUserId()));
            assertTrue(users.contains(friendship.secondUserId()));
            assertTrue(friendship.firstUserId() < friendship.secondUserId());
            store.getFriendRequests().stream().filter(r -> r.getStatus() == FriendRequestStatus.ACCEPTED
                    && Set.of(r.getSenderId(), r.getReceiverId()).equals(Set.of(friendship.firstUserId(), friendship.secondUserId())))
                    .forEach(request -> assertFalse(friendship.createdAt().isBefore(request.getRespondedAt()),
                            "Friendship cannot predate acceptance"));
        }
        for (var block : store.getBlocks()) {
            assertTrue(users.contains(block.blockerId()));
            assertTrue(users.contains(block.blockedId()));
            assertNotEquals(block.blockerId(), block.blockedId());
            assertTrue(store.getFriendships().stream().noneMatch(f -> Set.of(f.firstUserId(), f.secondUserId()).equals(Set.of(block.blockerId(), block.blockedId()))));
            assertTrue(store.getFriendRequests().stream().noneMatch(r -> r.getStatus() == FriendRequestStatus.PENDING && Set.of(r.getSenderId(), r.getReceiverId()).equals(Set.of(block.blockerId(), block.blockedId()))));
        }
        for (GroupInvitation invitation : store.getInvitations()) {
            assertTrue(users.contains(invitation.getInviterId()));
            assertTrue(users.contains(invitation.getInviteeId()));
            assertTrue(store.getConversations().stream().anyMatch(c -> c.getId() == invitation.getConversationId() && c.getType() == ConversationType.GROUP));
            assertTrue(store.getMembers().stream().anyMatch(m -> m.getConversationId() == invitation.getConversationId() && m.getUserId() == invitation.getInviterId() && m.getRole() == GroupRole.ADMIN));
            assertEquals(invitation.getStatus() == FriendRequestStatus.PENDING, invitation.getRespondedAt() == null);
            if (invitation.getStatus() == FriendRequestStatus.PENDING) {
                assertFalse(membershipKeys.contains(invitation.getConversationId() + ":" + invitation.getInviteeId()));
            }
            if (invitation.getStatus() == FriendRequestStatus.ACCEPTED) {
                ConversationMember joined = store.getMembers().stream().filter(m -> m.getConversationId() == invitation.getConversationId()
                        && m.getUserId() == invitation.getInviteeId()).findFirst().orElseThrow();
                assertEquals(invitation.getRespondedAt(), joined.getJoinedAt());
            }
        }
        for (SpamReport report : store.getReports()) {
            Message message = store.getMessages().stream().filter(m -> m.getId() == report.getMessageId()).findFirst().orElseThrow();
            assertEquals(message.getSenderId(), report.getReportedUserId());
            assertNotEquals(report.getReporterId(), report.getReportedUserId());
            assertTrue(membershipKeys.contains(message.getConversationId() + ":" + report.getReporterId()));
            assertFalse(report.getCreatedAt().isBefore(message.getSentAt()));
        }
        store.getLoginEvents().forEach(event -> assertTrue(users.contains(event.userId())));
        store.getActivityEvents().forEach(event -> {
            assertTrue(users.contains(event.userId()));
            if (event.conversationId() != null) assertTrue(conversations.contains(event.conversationId()));
            if (event.type() == ActivityType.MESSAGE_SENT) assertTrue(membershipKeys.contains(event.conversationId() + ":" + event.userId()));
        });
    }

    @Test
    void fixturesProvidePendingAndProcessedStatesWithoutContradictoryRelationships() {
        MockDataStore store = MockFixtures.createStore();
        assertTrue(store.getConversations().stream().anyMatch(c -> c.getId() == MockFixtures.DIRECT_CONVERSATION_ID && c.getType() == ConversationType.DIRECT));
        assertTrue(store.getConversations().stream().anyMatch(c -> c.getId() == MockFixtures.GROUP_CONVERSATION_ID && c.getType() == ConversationType.GROUP));
        assertEquals(Set.of(FriendRequestStatus.values()), store.getFriendRequests().stream().map(FriendRequest::getStatus).collect(Collectors.toSet()));
        assertEquals(Set.of(ReportStatus.values()), store.getReports().stream().map(SpamReport::getStatus).collect(Collectors.toSet()));
        assertTrue(store.getInvitations().stream().anyMatch(i -> i.getStatus() == FriendRequestStatus.PENDING && i.getInviteeId() == MockFixtures.DEMO_USER_ID));
        assertFalse(store.getFriendships().isEmpty());
        assertFalse(store.getBlocks().isEmpty());
        assertFalse(store.getLoginEvents().isEmpty());
        assertEquals(Set.of(ActivityType.APP_OPEN, ActivityType.MESSAGE_SENT), store.getActivityEvents().stream().map(ActivityEvent::type).collect(Collectors.toSet()));
    }

    @Test
    void freshStoresDoNotShareMutableAccountsSettingsRowsOrCredentials() {
        MockDataStore first = MockFixtures.createStore();
        MockDataStore second = MockFixtures.createStore();
        first.getUsers().get(0).updateProfile("Changed", "changed", "Changed bio");
        first.getSettings().get(0).updatePreferences(false, true, false);
        first.getMembers().get(0).clearHistoryThrough(3);
        first.getConversations().get(0).updatePreview("Different", MockFixtures.REFERENCE_TIME);
        first.getFriendRequests().get(0).respond(FriendRequestStatus.REJECTED, MockFixtures.REFERENCE_TIME);
        first.getInvitations().get(0).respond(FriendRequestStatus.REJECTED, MockFixtures.REFERENCE_TIME);
        first.getReports().get(0).review(ReportStatus.REVIEWED, "Different review");
        first.passwords.put(MockFixtures.DEMO_USER_ID, "changed");
        first.users.remove(first.users.size() - 1);
        assertEquals("Alex Morgan", second.getUsers().get(0).getDisplayName());
        assertTrue(second.getSettings().get(0).isDesktopNotifications());
        assertEquals(0, second.getMembers().get(0).getHistoryClearedThroughSeq());
        assertEquals("Great, see you at two!", second.getConversations().get(0).getLastMessagePreview());
        assertEquals(FriendRequestStatus.PENDING, second.getFriendRequests().get(0).getStatus());
        assertEquals(FriendRequestStatus.PENDING, second.getInvitations().get(0).getStatus());
        assertEquals(ReportStatus.PENDING, second.getReports().get(0).getStatus());
        assertEquals(MockFixtures.DEMO_PASSWORD, second.passwords.get(MockFixtures.DEMO_USER_ID));
        assertEquals(24, second.getUsers().size());
    }

    @Test
    void exposedCollectionsAreReadOnlyButObserveStoreChanges() {
        MockDataStore store = MockFixtures.createStore();
        int[] changes = {0};
        store.getUsers().addListener((javafx.collections.ListChangeListener<UserAccount>) change -> changes[0]++);
        assertThrows(UnsupportedOperationException.class, () -> store.getUsers().clear());
        assertThrows(UnsupportedOperationException.class, () -> store.getMembers().clear());
        assertThrows(UnsupportedOperationException.class, () -> store.getMessages().clear());
        assertThrows(UnsupportedOperationException.class, () -> store.getFriendships().clear());
        assertThrows(UnsupportedOperationException.class, () -> store.getBlocks().clear());
        assertThrows(UnsupportedOperationException.class, () -> store.getReports().clear());
        store.users.remove(store.users.size() - 1);
        assertEquals(1, changes[0]);
        assertEquals(23, store.getUsers().size());
    }
}
