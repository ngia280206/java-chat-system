package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.model.Conversation;
import com.chatsystem.shared.model.ConversationMember;
import com.chatsystem.shared.model.GroupInvitation;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.ConversationType;
import com.chatsystem.shared.model.status.FriendRequestStatus;
import com.chatsystem.shared.model.status.GroupRole;
import com.chatsystem.shared.model.status.UserStatus;
import com.chatsystem.shared.service.GroupService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.shared.validation.ValidationError;
import com.chatsystem.shared.validation.ValidationResult;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InMemoryGroupService implements GroupService {
    private final MockDataStore store;
    private final UserSession session;
    private final ObservableList<Conversation> groups = FXCollections.observableArrayList();
    private final ObservableList<GroupInvitation> invitations = FXCollections.observableArrayList();
    private final ObservableList<Conversation> groupsView;
    private final ObservableList<GroupInvitation> invitationsView;
    private final Map<Long, ObservableList<ConversationMember>> memberLists = new HashMap<>();
    private final Map<Long, ObservableList<ConversationMember>> memberViews = new HashMap<>();

    public InMemoryGroupService(MockDataStore store, UserSession session) {
        this.store = Objects.requireNonNull(store);
        this.session = Objects.requireNonNull(session);
        groupsView = FXCollections.unmodifiableObservableList(groups);
        invitationsView = FXCollections.unmodifiableObservableList(invitations);
        InvalidationListener refresh = ignored -> refresh();
        session.currentUserProperty().addListener(refresh);
        store.getUsers().addListener(refresh);
        store.getMembers().addListener(refresh);
        store.getConversations().addListener(refresh);
        store.getInvitations().addListener(refresh);
        refresh();
    }

    @Override public ObservableList<Conversation> getGroups() { return groupsView; }
    @Override public ObservableList<GroupInvitation> getInvitations() { return invitationsView; }

    @Override
    public ObservableList<ConversationMember> getMembers(long groupId) {
        return memberViews.computeIfAbsent(groupId, id -> {
            ObservableList<ConversationMember> list = FXCollections.observableArrayList();
            list.setAll(store.getMembers().stream().filter(m -> visibleMember(id, m)).toList());
            memberLists.put(id, list);
            return FXCollections.unmodifiableObservableList(list);
        });
    }

    @Override
    public ValidationResult<Conversation> createGroup(String name, String description) {
        UserAccount actor = actor();
        if (actor == null) return failure("Sign in with an active account first");
        var errors = detailErrors(name, description);
        if (!errors.isEmpty()) return ValidationResult.failure(errors);
        return ValidationResult.success(store.createMockGroup(actor.getId(), name.strip(), description));
    }

    @Override
    public ValidationResult<Void> updateGroup(long groupId, String name, String description) {
        if (!isAdmin(groupId)) return failure("Only a current group admin can edit this group");
        var errors = detailErrors(name, description);
        if (!errors.isEmpty()) return ValidationResult.failure(errors);
        group(groupId).updateDetails(name.strip(), description);
        return ValidationResult.success(null);
    }

    @Override
    public ValidationResult<GroupInvitation> inviteMember(long groupId, long userId) {
        if (!isAdmin(groupId)) return failure("Only a current group admin can invite members");
        if (!activeUser(userId) || currentMember(groupId, userId) != null) return failure("Choose an active account outside this group");
        if (store.getInvitations().stream().anyMatch(i -> i.getConversationId() == groupId && i.getInviteeId() == userId
                && i.getStatus() == FriendRequestStatus.PENDING)) return failure("This account already has a pending invitation");
        return ValidationResult.success(store.createMockGroupInvitation(groupId, actor().getId(), userId));
    }

    @Override
    public ValidationResult<Void> acceptInvitation(long invitationId) {
        GroupInvitation invitation = pendingForActor(invitationId);
        if (invitation == null) return failure("Choose a pending invitation addressed to your active account");
        long groupId = invitation.getConversationId();
        if (group(groupId) == null || currentMember(groupId, invitation.getInviteeId()) != null
                || currentMember(groupId, invitation.getInviterId()) == null || !activeUser(invitation.getInviterId())
                || !hasOtherAdmin(groupId, -1)) return failure("This invitation is no longer available");
        ConversationMember previous = membership(groupId, invitation.getInviteeId());
        if (previous == null) {
            store.addMockGroupMember(groupId, invitation.getInviteeId());
        } else {
            previous.setRole(GroupRole.MEMBER);
            previous.setLeftAt(null);
        }
        invitation.respond(FriendRequestStatus.ACCEPTED, Instant.now());
        return ValidationResult.success(null);
    }

    @Override
    public ValidationResult<Void> rejectInvitation(long invitationId) {
        GroupInvitation invitation = pendingForActor(invitationId);
        if (invitation == null || group(invitation.getConversationId()) == null) {
            return failure("Choose a pending invitation addressed to your active account");
        }
        invitation.respond(FriendRequestStatus.REJECTED, Instant.now());
        return ValidationResult.success(null);
    }

    @Override
    public ValidationResult<Void> removeMember(long groupId, long userId) {
        if (!isAdmin(groupId)) return failure("Only a current group admin can remove members");
        ConversationMember target = currentMember(groupId, userId);
        if (target == null) return failure("Choose a current group member");
        return depart(groupId, target);
    }

    @Override
    public ValidationResult<Void> leaveGroup(long groupId) {
        UserAccount actor = actor();
        if (actor == null || group(groupId) == null) return failure("Choose a group you currently belong to");
        ConversationMember target = currentMember(groupId, actor.getId());
        if (target == null) return failure("Choose a group you currently belong to");
        return depart(groupId, target);
    }

    @Override
    public ValidationResult<Void> changeRole(long groupId, long userId, GroupRole role) {
        if (!isAdmin(groupId)) return failure("Only a current group admin can change roles");
        ConversationMember target = currentMember(groupId, userId);
        if (target == null || !activeUser(userId) || role == null || role == target.getRole()) {
            return failure("Choose a current active member and a different role");
        }
        if (role == GroupRole.MEMBER && !hasOtherAdmin(groupId, userId)) {
            return failure("Keep at least one active group admin");
        }
        target.setRole(role);
        return ValidationResult.success(null);
    }

    private ValidationResult<Void> depart(long groupId, ConversationMember target) {
        if (!hasOtherAdmin(groupId, target.getUserId())) return failure("Keep at least one active group admin");
        target.setLeftAt(Instant.now());
        return ValidationResult.success(null);
    }

    private void refresh() {
        UserAccount actor = actor();
        groups.setAll(store.getConversations().stream().filter(g -> actor != null && g.getType() == ConversationType.GROUP
                && currentMember(g.getId(), actor.getId()) != null).toList());
        invitations.setAll(store.getInvitations().stream().filter(i -> actor != null && i.getStatus() == FriendRequestStatus.PENDING
                && i.getInviteeId() == actor.getId()).toList());
        memberLists.forEach((id, list) -> list.setAll(store.getMembers().stream().filter(m -> visibleMember(id, m)).toList()));
    }

    private boolean visibleMember(long groupId, ConversationMember member) {
        UserAccount actor = actor();
        return actor != null && group(groupId) != null && currentMember(groupId, actor.getId()) != null
                && member.getConversationId() == groupId && member.getLeftAt() == null;
    }

    private boolean isAdmin(long groupId) {
        UserAccount actor = actor();
        if (actor == null || group(groupId) == null) return false;
        ConversationMember member = currentMember(groupId, actor.getId());
        return member != null && member.getRole() == GroupRole.ADMIN;
    }

    private boolean hasOtherAdmin(long groupId, long excludedUserId) {
        return store.getMembers().stream().anyMatch(m -> m.getConversationId() == groupId && m.getUserId() != excludedUserId
                && m.getLeftAt() == null && m.getRole() == GroupRole.ADMIN && activeUser(m.getUserId()));
    }

    private GroupInvitation pendingForActor(long id) {
        UserAccount actor = actor();
        if (actor == null) return null;
        return store.getInvitations().stream().filter(i -> i.getId() == id && i.getStatus() == FriendRequestStatus.PENDING
                && i.getInviteeId() == actor.getId()).findFirst().orElse(null);
    }

    private Conversation group(long id) {
        return store.getConversations().stream().filter(g -> g.getId() == id && g.getType() == ConversationType.GROUP).findFirst().orElse(null);
    }

    private ConversationMember membership(long groupId, long userId) {
        return store.getMembers().stream().filter(m -> m.getConversationId() == groupId && m.getUserId() == userId).findFirst().orElse(null);
    }

    private ConversationMember currentMember(long groupId, long userId) {
        ConversationMember member = membership(groupId, userId);
        return member != null && member.getLeftAt() == null ? member : null;
    }

    private boolean activeUser(long id) {
        return store.getUsers().stream().anyMatch(u -> u.getId() == id && u.getStatus() == UserStatus.ACTIVE);
    }

    private UserAccount actor() {
        UserAccount actor = session.getCurrentUser();
        return actor != null && actor.getStatus() == UserStatus.ACTIVE && store.getUsers().contains(actor) ? actor : null;
    }

    private static List<ValidationError> detailErrors(String name, String description) {
        var errors = new java.util.ArrayList<ValidationError>();
        if (name == null || name.isBlank()) errors.add(ValidationError.field("name", "Group name is required"));
        if (description == null) errors.add(ValidationError.field("description", "Description is required; it may be empty"));
        return errors;
    }

    private static <T> ValidationResult<T> failure(String message) {
        return ValidationResult.failure(List.of(ValidationError.global(message)));
    }
}
