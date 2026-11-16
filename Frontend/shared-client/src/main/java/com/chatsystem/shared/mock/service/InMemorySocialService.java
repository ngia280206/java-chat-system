package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.model.FriendRequest;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.FriendRequestStatus;
import com.chatsystem.shared.model.status.UserStatus;
import com.chatsystem.shared.service.SocialService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.shared.validation.ValidationError;
import com.chatsystem.shared.validation.ValidationResult;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class InMemorySocialService implements SocialService {
    private final MockDataStore store;
    private final UserSession session;
    private final ObservableList<UserAccount> friends = FXCollections.observableArrayList();
    private final ObservableList<UserAccount> blocked = FXCollections.observableArrayList();
    private final ObservableList<FriendRequest> incoming = FXCollections.observableArrayList();
    private final ObservableList<FriendRequest> outgoing = FXCollections.observableArrayList();
    private final ObservableList<UserAccount> friendsView;
    private final ObservableList<UserAccount> blockedView;
    private final ObservableList<FriendRequest> incomingView;
    private final ObservableList<FriendRequest> outgoingView;

    public InMemorySocialService(MockDataStore store, UserSession session) {
        this.store = Objects.requireNonNull(store);
        this.session = Objects.requireNonNull(session);
        friendsView = FXCollections.unmodifiableObservableList(friends);
        blockedView = FXCollections.unmodifiableObservableList(blocked);
        incomingView = FXCollections.unmodifiableObservableList(incoming);
        outgoingView = FXCollections.unmodifiableObservableList(outgoing);
        InvalidationListener refresh = ignored -> refresh();
        session.currentUserProperty().addListener(refresh);
        store.getUsers().addListener(refresh);
        store.getFriendships().addListener(refresh);
        store.getBlocks().addListener(refresh);
        store.getFriendRequests().addListener(refresh);
        refresh();
    }

    @Override public ObservableList<UserAccount> getFriends() { return friendsView; }
    @Override public ObservableList<FriendRequest> getIncomingRequests() { return incomingView; }
    @Override public ObservableList<FriendRequest> getOutgoingRequests() { return outgoingView; }
    @Override public ObservableList<UserAccount> getBlockedUsers() { return blockedView; }

    @Override
    public ValidationResult<FriendRequest> sendRequest(long userId) {
        UserAccount actor = actor();
        if (actor == null) return failure("Sign in with an active account first");
        UserAccount target = user(userId);
        if (target == null || target.getStatus() != UserStatus.ACTIVE || userId == actor.getId()) {
            return failure("Choose another active account");
        }
        if (blockedEitherWay(actor.getId(), userId)) return failure("Requests are unavailable between blocked accounts");
        if (areFriends(actor.getId(), userId)) return failure("This account is already a friend");
        if (store.getFriendRequests().stream().anyMatch(r -> r.getStatus() == FriendRequestStatus.PENDING
                && connects(r, actor.getId(), userId))) return failure("A friend request is already pending");
        return ValidationResult.success(store.createMockFriendRequest(actor.getId(), userId));
    }

    @Override
    public ValidationResult<Void> acceptRequest(long requestId) {
        FriendRequest request = pendingForActor(requestId, false);
        if (request == null) return failure("Choose a pending request addressed to your active account");
        UserAccount sender = user(request.getSenderId());
        if (sender == null || sender.getStatus() != UserStatus.ACTIVE
                || blockedEitherWay(request.getSenderId(), request.getReceiverId())
                || areFriends(request.getSenderId(), request.getReceiverId())) {
            return failure("This friend request is no longer available");
        }
        request.respond(FriendRequestStatus.ACCEPTED, Instant.now());
        store.addMockFriendship(request.getSenderId(), request.getReceiverId());
        return ValidationResult.success(null);
    }

    @Override public ValidationResult<Void> rejectRequest(long requestId) {
        return resolveRequest(requestId, false, FriendRequestStatus.REJECTED);
    }

    @Override public ValidationResult<Void> cancelRequest(long requestId) {
        return resolveRequest(requestId, true, FriendRequestStatus.CANCELLED);
    }

    @Override
    public ValidationResult<Void> blockUser(long userId) {
        UserAccount actor = actor();
        if (actor == null) return failure("Sign in with an active account first");
        if (user(userId) == null || userId == actor.getId()) return failure("Choose another existing account");
        if (hasBlock(actor.getId(), userId)) return failure("This account is already blocked");
        store.removeMockFriendship(actor.getId(), userId);
        // Snapshot before changing observable statuses, which update the source list.
        store.getFriendRequests().stream().filter(r -> r.getStatus() == FriendRequestStatus.PENDING
                && connects(r, actor.getId(), userId)).toList()
                .forEach(r -> r.respond(FriendRequestStatus.CANCELLED, Instant.now()));
        store.addMockBlock(actor.getId(), userId);
        return ValidationResult.success(null);
    }

    @Override
    public ValidationResult<Void> unblockUser(long userId) {
        UserAccount actor = actor();
        if (actor == null) return failure("Sign in with an active account first");
        if (user(userId) == null || !hasBlock(actor.getId(), userId)) return failure("Choose an account you currently block");
        store.removeMockBlock(actor.getId(), userId);
        return ValidationResult.success(null);
    }

    private ValidationResult<Void> resolveRequest(long id, boolean sent, FriendRequestStatus status) {
        FriendRequest request = pendingForActor(id, sent);
        if (request == null) return failure("Choose a pending request belonging to your active account");
        request.respond(status, Instant.now());
        return ValidationResult.success(null);
    }

    private FriendRequest pendingForActor(long id, boolean sent) {
        UserAccount actor = actor();
        if (actor == null) return null;
        return store.getFriendRequests().stream().filter(r -> r.getId() == id && r.getStatus() == FriendRequestStatus.PENDING
                && (sent ? r.getSenderId() : r.getReceiverId()) == actor.getId()).findFirst().orElse(null);
    }

    private void refresh() {
        UserAccount actor = actor();
        friends.setAll(store.getUsers().stream().filter(u -> actor != null && areFriends(actor.getId(), u.getId())).toList());
        blocked.setAll(store.getUsers().stream().filter(u -> actor != null && hasBlock(actor.getId(), u.getId())).toList());
        incoming.setAll(store.getFriendRequests().stream().filter(r -> actor != null
                && r.getStatus() == FriendRequestStatus.PENDING && r.getReceiverId() == actor.getId()).toList());
        outgoing.setAll(store.getFriendRequests().stream().filter(r -> actor != null
                && r.getStatus() == FriendRequestStatus.PENDING && r.getSenderId() == actor.getId()).toList());
    }

    private UserAccount actor() {
        UserAccount actor = session.getCurrentUser();
        return actor != null && actor.getStatus() == UserStatus.ACTIVE && store.getUsers().contains(actor) ? actor : null;
    }

    private UserAccount user(long id) { return store.getUsers().stream().filter(u -> u.getId() == id).findFirst().orElse(null); }
    private boolean hasBlock(long from, long to) { return store.getBlocks().stream().anyMatch(b -> b.blockerId() == from && b.blockedId() == to); }
    private boolean blockedEitherWay(long first, long second) { return hasBlock(first, second) || hasBlock(second, first); }
    private boolean areFriends(long first, long second) {
        return store.getFriendships().stream().anyMatch(f -> f.firstUserId() == Math.min(first, second)
                && f.secondUserId() == Math.max(first, second));
    }
    private static boolean connects(FriendRequest r, long first, long second) {
        return (r.getSenderId() == first && r.getReceiverId() == second) || (r.getSenderId() == second && r.getReceiverId() == first);
    }
    private static <T> ValidationResult<T> failure(String message) {
        return ValidationResult.failure(List.of(ValidationError.global(message)));
    }
}
