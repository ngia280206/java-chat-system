package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.FriendRequest;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.FriendRequestStatus;
import com.chatsystem.shared.model.status.UserStatus;
import com.chatsystem.shared.service.SocialService;
import javafx.collections.ListChangeListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemorySocialServiceTest {
    private MockDataStore store;
    private InMemoryAuthService auth;
    private SocialService social;

    @BeforeEach
    void setUp() {
        store = MockFixtures.createStore();
        auth = new InMemoryAuthService(store);
        social = new InMemorySocialService(store, auth.getSession());
        login(1);
    }

    @Test
    void acceptingRequestUpdatesHeldProjectionsAndSharedServiceInstances() {
        var incoming = social.getIncomingRequests();
        var friends = social.getFriends();
        SocialService other = new InMemorySocialService(store, auth.getSession());
        List<Long> observedFriends = new ArrayList<>();
        friends.addListener((ListChangeListener<UserAccount>) c -> observedFriends.addAll(ids(friends)));
        FriendRequest request = incoming.get(0);
        assertEquals(301, request.getId());
        assertTrue(social.acceptRequest(301).isSuccess());
        assertTrue(incoming.isEmpty());
        assertEquals(List.of(2L, 4L, 5L, 6L), ids(friends));
        assertEquals(ids(friends), ids(other.getFriends()));
        assertTrue(observedFriends.contains(6L));
        assertEquals(FriendRequestStatus.ACCEPTED, request.getStatus());
        assertNotNull(request.getRespondedAt());
        login(6);
        assertEquals(List.of(1L), ids(friends));
        assertFalse(social.acceptRequest(301).isSuccess());
    }

    @Test
    void sendingAndCancellingRequestUpdatesBothPartiesAndKeepsHistory() {
        var outgoing = social.getOutgoingRequests();
        var incoming = social.getIncomingRequests();
        var request = social.sendRequest(10).value().orElseThrow();
        assertTrue(outgoing.contains(request));
        login(10);
        assertTrue(incoming.contains(request));
        assertFalse(social.cancelRequest(request.getId()).isSuccess());
        login(1);
        assertTrue(social.cancelRequest(request.getId()).isSuccess());
        assertFalse(outgoing.contains(request));
        assertEquals(FriendRequestStatus.CANCELLED, request.getStatus());
        assertTrue(store.getFriendRequests().contains(request));
        assertFalse(social.cancelRequest(request.getId()).isSuccess());
        assertTrue(social.sendRequest(10).isSuccess());
    }

    @Test
    void onlyReceiverCanRejectAndOnlySenderCanCancelPendingRequest() {
        assertFalse(social.rejectRequest(302).isSuccess());
        assertFalse(social.acceptRequest(302).isSuccess());
        assertFalse(social.cancelRequest(301).isSuccess());
        assertTrue(social.rejectRequest(301).isSuccess());
        assertTrue(social.getIncomingRequests().isEmpty());
        assertEquals(FriendRequestStatus.REJECTED, request(301).getStatus());
        assertFalse(social.acceptRequest(301).isSuccess());
        assertEquals(List.of(2L, 4L, 5L), ids(social.getFriends()));
    }

    @Test
    void invalidAndDuplicateRequestsDoNotMutateState() {
        var before = snapshot();
        for (long id : new long[]{1, 2, 3, 6, 7, 999}) {
            assertFalse(social.sendRequest(id).isSuccess(), "target " + id);
        }
        assertFalse(social.acceptRequest(999).isSuccess());
        assertFalse(social.rejectRequest(999).isSuccess());
        assertFalse(social.cancelRequest(999).isSuccess());
        assertEquals(before, snapshot());
    }

    @Test
    void blockingRemovesFriendshipOnBothSidesAndUnblockDoesNotRestoreIt() {
        var friends = social.getFriends();
        var blocks = social.getBlockedUsers();
        assertTrue(social.blockUser(2).isSuccess());
        assertEquals(List.of(4L, 5L), ids(friends));
        assertEquals(List.of(2L, 3L), ids(blocks));
        login(2);
        assertTrue(friends.isEmpty());
        assertTrue(blocks.isEmpty());
        assertFalse(social.sendRequest(1).isSuccess());
        assertFalse(social.unblockUser(1).isSuccess());
        assertTrue(social.blockUser(1).isSuccess());
        login(1);
        assertTrue(social.unblockUser(2).isSuccess());
        assertEquals(List.of(3L), ids(blocks));
        assertFalse(social.sendRequest(2).isSuccess());
        login(2);
        assertTrue(social.unblockUser(1).isSuccess());
        login(1);
        assertFalse(ids(friends).contains(2L));
        assertTrue(social.sendRequest(2).isSuccess());
    }

    @Test
    void blockCancelsIncomingAndOutgoingPendingRequestsWithoutRestoringThem() {
        assertTrue(social.blockUser(6).isSuccess());
        assertTrue(social.blockUser(7).isSuccess());
        assertTrue(social.getIncomingRequests().isEmpty());
        assertTrue(social.getOutgoingRequests().isEmpty());
        assertEquals(FriendRequestStatus.CANCELLED, request(301).getStatus());
        assertEquals(FriendRequestStatus.CANCELLED, request(302).getStatus());
        assertFalse(social.acceptRequest(301).isSuccess());
        assertTrue(social.unblockUser(6).isSuccess());
        assertTrue(social.getIncomingRequests().isEmpty());
        assertTrue(social.sendRequest(6).isSuccess());
    }

    @Test
    void selfUnknownDuplicateBlockAndMissingUnblockFailWithoutMutation() {
        var before = snapshot();
        assertFalse(social.blockUser(1).isSuccess());
        assertFalse(social.blockUser(999).isSuccess());
        assertFalse(social.blockUser(3).isSuccess());
        assertFalse(social.unblockUser(1).isSuccess());
        assertFalse(social.unblockUser(2).isSuccess());
        assertFalse(social.unblockUser(999).isSuccess());
        assertEquals(before, snapshot());
    }

    @Test
    void inactiveRequestSenderCannotBeAccepted() {
        user(6).setStatus(UserStatus.LOCKED);
        var before = snapshot();
        assertFalse(social.acceptRequest(301).isSuccess());
        assertEquals(before, snapshot());
        assertTrue(social.rejectRequest(301).isSuccess());
    }

    @Test
    void projectionsAreReadOnlyAndClearWithInactiveOrLoggedOutSession() {
        assertThrows(UnsupportedOperationException.class, () -> social.getFriends().clear());
        assertThrows(UnsupportedOperationException.class, () -> social.getIncomingRequests().clear());
        assertThrows(UnsupportedOperationException.class, () -> social.getOutgoingRequests().clear());
        assertThrows(UnsupportedOperationException.class, () -> social.getBlockedUsers().clear());
        var friends = social.getFriends();
        var before = snapshot();
        user(1).setStatus(UserStatus.LOCKED);
        assertTrue(friends.isEmpty());
        assertAllOperationsRejected();
        assertEquals(before, snapshot());
        user(1).setStatus(UserStatus.ACTIVE);
        assertEquals(3, friends.size());
        auth.logout();
        assertTrue(friends.isEmpty());
        assertTrue(social.getIncomingRequests().isEmpty());
        assertTrue(social.getOutgoingRequests().isEmpty());
        assertTrue(social.getBlockedUsers().isEmpty());
        assertAllOperationsRejected();
        assertEquals(before, snapshot());
    }

    @Test
    void sessionFromDifferentStoreCannotReadOrMutateRelationships() {
        SocialService mismatched = new InMemorySocialService(MockFixtures.createStore(), auth.getSession());
        assertTrue(mismatched.getFriends().isEmpty());
        assertTrue(mismatched.getIncomingRequests().isEmpty());
        assertFalse(mismatched.sendRequest(10).isSuccess());
        assertFalse(mismatched.blockUser(2).isSuccess());
    }

    private void assertAllOperationsRejected() {
        assertFalse(social.sendRequest(10).isSuccess());
        assertFalse(social.acceptRequest(301).isSuccess());
        assertFalse(social.rejectRequest(301).isSuccess());
        assertFalse(social.cancelRequest(302).isSuccess());
        assertFalse(social.blockUser(2).isSuccess());
        assertFalse(social.unblockUser(3).isSuccess());
    }

    private List<Object> snapshot() {
        return List.of(List.copyOf(store.getFriendships()), List.copyOf(store.getBlocks()),
                store.getFriendRequests().stream().map(r -> List.of(r.getId(), r.getStatus(),
                        String.valueOf(r.getRespondedAt()))).toList());
    }

    private FriendRequest request(long id) {
        return store.getFriendRequests().stream().filter(r -> r.getId() == id).findFirst().orElseThrow();
    }

    private UserAccount user(long id) { return store.getUsers().stream().filter(u -> u.getId() == id).findFirst().orElseThrow(); }
    private void login(long id) { assertTrue(auth.login(user(id).getEmail(), MockFixtures.DEMO_PASSWORD, false).isSuccess()); }
    private static List<Long> ids(List<UserAccount> users) { return users.stream().map(UserAccount::getId).toList(); }
}
