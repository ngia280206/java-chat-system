package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.service.GroupService;
import javafx.collections.ListChangeListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryGroupServiceTest {
    private MockDataStore store;
    private InMemoryAuthService auth;
    private GroupService groups;

    @BeforeEach
    void setUp() {
        store = MockFixtures.createStore();
        auth = new InMemoryAuthService(store);
        groups = new InMemoryGroupService(store, auth.getSession());
        login(1);
    }

    @Test
    void createAddsGroupConversationAndCreatorAdminToHeldProjections() {
        var list = groups.getGroups();
        List<Long> changes = new ArrayList<>();
        list.addListener((ListChangeListener<Conversation>) c -> changes.addAll(groupIds(list)));
        Conversation group = groups.createGroup(" New group ", "Description").value().orElseThrow();
        assertEquals("New group", group.getName());
        assertEquals("Description", group.getDescription());
        assertEquals(ConversationType.GROUP, group.getType());
        assertEquals(1, group.getCreatedBy());
        assertTrue(store.getConversations().contains(group));
        assertTrue(list.contains(group));
        assertTrue(changes.contains(group.getId()));
        assertEquals(List.of(1L), memberIds(groups.getMembers(group.getId())));
        assertEquals(GroupRole.ADMIN, groups.getMembers(group.getId()).get(0).getRole());
    }

    @Test
    void acceptingInvitationUpdatesGroupsMembersAndInvitationAcrossInstances() {
        var invitations = groups.getInvitations();
        var list = groups.getGroups();
        GroupService other = new InMemoryGroupService(store, auth.getSession());
        var members = groups.getMembers(21);
        GroupInvitation invitation = invitations.get(0);
        assertTrue(members.isEmpty());
        assertTrue(groups.acceptInvitation(401).isSuccess());
        assertTrue(invitations.isEmpty());
        assertEquals(FriendRequestStatus.ACCEPTED, invitation.getStatus());
        assertNotNull(invitation.getRespondedAt());
        assertEquals(List.of(20L, 21L, 22L), groupIds(list));
        assertEquals(groupIds(list), groupIds(other.getGroups()));
        assertEquals(List.of(2L, 5L, 6L, 1L), memberIds(members));
        assertEquals(GroupRole.MEMBER, member(21, 1).getRole());
        assertFalse(groups.acceptInvitation(401).isSuccess());
        assertTrue(groups.leaveGroup(21).isSuccess());
        assertFalse(groupIds(list).contains(21L));
        assertTrue(members.isEmpty());
        assertNotNull(member(21, 1).getLeftAt());
    }

    @Test
    void inviteJoinLeaveAndRejoinNeverDuplicateMembershipIdentity() {
        var members = groups.getMembers(20);
        var invitation = groups.inviteMember(20, 7).value().orElseThrow();
        login(7);
        assertTrue(groups.getInvitations().contains(invitation));
        assertTrue(groups.acceptInvitation(invitation.getId()).isSuccess());
        var joined = member(20, 7);
        assertTrue(members.contains(joined));
        assertTrue(groups.leaveGroup(20).isSuccess());
        assertTrue(members.isEmpty());
        assertFalse(groups.changeRole(20, 7, GroupRole.ADMIN).isSuccess());
        login(1);
        assertFalse(memberIds(members).contains(7L));
        var second = groups.inviteMember(20, 7).value().orElseThrow();
        assertNotEquals(invitation.getId(), second.getId());
        login(7);
        assertTrue(groups.acceptInvitation(second.getId()).isSuccess());
        assertSame(joined, member(20, 7));
        assertEquals(GroupRole.MEMBER, joined.getRole());
        assertNull(joined.getLeftAt());
        assertEquals(1, store.getMembers().stream().filter(m -> m.getConversationId() == 20 && m.getUserId() == 7).count());
    }

    @Test
    void rejectingInvitationResolvesItWithoutAddingGroupOrMember() {
        assertTrue(groups.rejectInvitation(401).isSuccess());
        assertTrue(groups.getInvitations().isEmpty());
        assertEquals(List.of(20L, 22L), groupIds(groups.getGroups()));
        assertEquals(FriendRequestStatus.REJECTED, invitation(401).getStatus());
        assertFalse(groups.rejectInvitation(401).isSuccess());
        assertFalse(groups.acceptInvitation(401).isSuccess());
    }

    @Test
    void onlyInviteeCanResolveInvitation() {
        login(2);
        var before = snapshot();
        assertFalse(groups.acceptInvitation(401).isSuccess());
        assertFalse(groups.rejectInvitation(401).isSuccess());
        assertEquals(before, snapshot());
    }

    @Test
    void adminEditsExistingIdentityAndRoleChangesNotifyHeldMember() {
        Conversation group = groups.getGroups().get(0);
        var displayedMember = groups.getMembers(20).stream().filter(m -> m.getUserId() == 2).findFirst().orElseThrow();
        List<String> names = new ArrayList<>();
        List<GroupRole> roles = new ArrayList<>();
        group.nameProperty().addListener((o, oldValue, value) -> names.add(value));
        displayedMember.roleProperty().addListener((o, oldValue, value) -> roles.add(value));
        assertTrue(groups.updateGroup(20, " Renamed ", "Updated description").isSuccess());
        assertSame(group, groups.getGroups().get(0));
        assertEquals(List.of("Renamed"), names);
        assertEquals("Updated description", group.getDescription());
        assertTrue(groups.changeRole(20, 2, GroupRole.ADMIN).isSuccess());
        assertEquals(List.of(GroupRole.ADMIN), roles);
        login(2);
        assertTrue(groups.updateGroup(20, "Mia's group", "").isSuccess());
        assertTrue(groups.changeRole(20, 1, GroupRole.MEMBER).isSuccess());
        login(1);
        assertFalse(groups.updateGroup(20, "Denied", "").isSuccess());
    }

    @Test
    void ordinaryMemberCannotEditInviteRemoveOrChangeRole() {
        login(2);
        var before = snapshot();
        assertFalse(groups.updateGroup(20, "Denied", "").isSuccess());
        assertFalse(groups.inviteMember(20, 7).isSuccess());
        assertFalse(groups.removeMember(20, 4).isSuccess());
        assertFalse(groups.changeRole(20, 2, GroupRole.ADMIN).isSuccess());
        assertEquals(before, snapshot());
    }

    @Test
    void removedMemberLosesGroupAndPermissionInCurrentSession() {
        InMemoryAuthService memberAuth = new InMemoryAuthService(store);
        assertTrue(memberAuth.login(user(2).getEmail(), MockFixtures.DEMO_PASSWORD, false).isSuccess());
        GroupService memberService = new InMemoryGroupService(store, memberAuth.getSession());
        var memberGroups = memberService.getGroups();
        var memberProjection = memberService.getMembers(20);
        assertTrue(groups.removeMember(20, 2).isSuccess());
        assertNotNull(member(20, 2).getLeftAt());
        assertFalse(memberIds(groups.getMembers(20)).contains(2L));
        assertFalse(groupIds(memberGroups).contains(20L));
        assertTrue(memberProjection.isEmpty());
        assertFalse(memberService.leaveGroup(20).isSuccess());
        assertFalse(memberService.updateGroup(20, "Denied", "").isSuccess());
        assertFalse(groups.removeMember(20, 2).isSuccess());
        assertFalse(groups.changeRole(20, 2, GroupRole.ADMIN).isSuccess());
    }

    @Test
    void lastAdminCannotLeaveRemoveSelfOrDemoteEvenForSingleMemberGroup() {
        var before = snapshot();
        assertFalse(groups.leaveGroup(20).isSuccess());
        assertFalse(groups.removeMember(20, 1).isSuccess());
        assertFalse(groups.changeRole(20, 1, GroupRole.MEMBER).isSuccess());
        assertEquals(before, snapshot());
        long id = groups.createGroup("Solo", "").value().orElseThrow().getId();
        assertFalse(groups.leaveGroup(id).isSuccess());
        assertFalse(groups.removeMember(id, 1).isSuccess());
        assertFalse(groups.changeRole(id, 1, GroupRole.MEMBER).isSuccess());
        assertNull(member(id, 1).getLeftAt());
    }

    @Test
    void adminMayLeaveOrRemoveAnotherAdminOnlyWhenAnActiveAdminRemains() {
        assertTrue(groups.changeRole(20, 2, GroupRole.ADMIN).isSuccess());
        assertTrue(groups.leaveGroup(20).isSuccess());
        login(2);
        assertTrue(groups.changeRole(20, 4, GroupRole.ADMIN).isSuccess());
        assertTrue(groups.removeMember(20, 4).isSuccess());
        assertEquals(GroupRole.ADMIN, member(20, 2).getRole());
        assertFalse(groups.leaveGroup(20).isSuccess());
    }

    @Test
    void inactiveAdminsDoNotSatisfyLastAdminGuard() {
        assertTrue(groups.changeRole(20, 2, GroupRole.ADMIN).isSuccess());
        user(2).setStatus(UserStatus.LOCKED);
        var before = snapshot();
        assertFalse(groups.leaveGroup(20).isSuccess());
        assertFalse(groups.removeMember(20, 1).isSuccess());
        assertFalse(groups.changeRole(20, 1, GroupRole.MEMBER).isSuccess());
        assertEquals(before, snapshot());
    }

    @Test
    void removedAdminsDoNotSatisfyLastAdminGuardOrRetainPermissions() {
        assertTrue(groups.changeRole(20, 2, GroupRole.ADMIN).isSuccess());
        assertTrue(groups.removeMember(20, 2).isSuccess());
        assertFalse(groups.leaveGroup(20).isSuccess());
        login(2);
        assertFalse(groups.changeRole(20, 2, GroupRole.ADMIN).isSuccess());
        assertFalse(groups.inviteMember(20, 7).isSuccess());
    }

    @Test
    void invalidFieldsTargetsRolesAndDuplicateInvitesDoNotMutateState() {
        var before = snapshot();
        assertFalse(groups.createGroup(" ", "").isSuccess());
        assertFalse(groups.createGroup(null, "").isSuccess());
        assertFalse(groups.createGroup("Name", null).isSuccess());
        assertFalse(groups.updateGroup(20, " ", "Changed").isSuccess());
        assertFalse(groups.updateGroup(20, "Changed", null).isSuccess());
        assertFalse(groups.changeRole(20, 2, null).isSuccess());
        assertFalse(groups.changeRole(20, 2, GroupRole.MEMBER).isSuccess());
        for (long id : new long[]{1, 2, 3, 999}) assertFalse(groups.inviteMember(20, id).isSuccess());
        assertEquals(before, snapshot());
        assertTrue(groups.inviteMember(20, 7).isSuccess());
        var withInvite = snapshot();
        assertFalse(groups.inviteMember(20, 7).isSuccess());
        assertEquals(withInvite, snapshot());
    }

    @Test
    void unknownDirectAndNonmemberGroupIdsFailWithoutMutation() {
        var before = snapshot();
        for (long id : new long[]{999, 10, 21}) {
            assertFalse(groups.updateGroup(id, "Denied", "").isSuccess());
            assertFalse(groups.inviteMember(id, 7).isSuccess());
            assertFalse(groups.removeMember(id, 2).isSuccess());
            assertFalse(groups.changeRole(id, 2, GroupRole.ADMIN).isSuccess());
            assertFalse(groups.leaveGroup(id).isSuccess());
            assertTrue(groups.getMembers(id).isEmpty());
        }
        assertFalse(groups.removeMember(20, 999).isSuccess());
        assertFalse(groups.changeRole(20, 999, GroupRole.ADMIN).isSuccess());
        assertFalse(groups.acceptInvitation(999).isSuccess());
        assertFalse(groups.rejectInvitation(999).isSuccess());
        assertEquals(before, snapshot());
    }

    @Test
    void staleInvitationFromDepartedInviterCannotAddMembership() {
        assertTrue(groups.changeRole(20, 2, GroupRole.ADMIN).isSuccess());
        var invite = groups.inviteMember(20, 7).value().orElseThrow();
        assertTrue(groups.leaveGroup(20).isSuccess());
        login(7);
        var before = snapshot();
        assertFalse(groups.acceptInvitation(invite.getId()).isSuccess());
        assertEquals(before, snapshot());
        assertTrue(groups.rejectInvitation(invite.getId()).isSuccess());
    }

    @Test
    void invitationCannotJoinGroupWhoseOnlyAdminBecameInactive() {
        var invite = groups.inviteMember(20, 7).value().orElseThrow();
        user(1).setStatus(UserStatus.LOCKED);
        login(7);
        var before = snapshot();
        assertFalse(groups.acceptInvitation(invite.getId()).isSuccess());
        assertEquals(before, snapshot());
    }

    @Test
    void readOnlyProjectionsFollowLogoutStatusChangesAndSessionIdentity() {
        var list = groups.getGroups();
        var invitations = groups.getInvitations();
        var members = groups.getMembers(20);
        assertThrows(UnsupportedOperationException.class, list::clear);
        assertThrows(UnsupportedOperationException.class, invitations::clear);
        assertThrows(UnsupportedOperationException.class, members::clear);
        user(1).setStatus(UserStatus.LOCKED);
        assertTrue(list.isEmpty());
        assertTrue(invitations.isEmpty());
        assertTrue(members.isEmpty());
        var before = snapshot();
        assertAllOperationsRejected();
        assertEquals(before, snapshot());
        user(1).setStatus(UserStatus.ACTIVE);
        assertEquals(List.of(20L, 22L), groupIds(list));
        auth.logout();
        assertTrue(list.isEmpty());
        assertTrue(invitations.isEmpty());
        assertTrue(members.isEmpty());
        assertAllOperationsRejected();
        assertEquals(before, snapshot());
        login(1);
        GroupService mismatched = new InMemoryGroupService(MockFixtures.createStore(), auth.getSession());
        assertTrue(mismatched.getGroups().isEmpty());
        assertFalse(mismatched.createGroup("Denied", "").isSuccess());
        assertFalse(mismatched.acceptInvitation(401).isSuccess());
    }

    private void assertAllOperationsRejected() {
        assertFalse(groups.createGroup("Denied", "").isSuccess());
        assertFalse(groups.updateGroup(20, "Denied", "").isSuccess());
        assertFalse(groups.inviteMember(20, 7).isSuccess());
        assertFalse(groups.acceptInvitation(401).isSuccess());
        assertFalse(groups.rejectInvitation(401).isSuccess());
        assertFalse(groups.removeMember(20, 2).isSuccess());
        assertFalse(groups.leaveGroup(20).isSuccess());
        assertFalse(groups.changeRole(20, 2, GroupRole.ADMIN).isSuccess());
    }

    private List<Object> snapshot() {
        return List.of(store.getConversations().stream().map(c -> List.of(c.getId(), c.getName(), c.getDescription())).toList(),
                store.getMembers().stream().map(m -> List.of(m.getConversationId(), m.getUserId(), m.getRole(), String.valueOf(m.getLeftAt()))).toList(),
                store.getInvitations().stream().map(i -> List.of(i.getId(), i.getStatus(), String.valueOf(i.getRespondedAt()))).toList());
    }

    private ConversationMember member(long groupId, long userId) {
        return store.getMembers().stream().filter(m -> m.getConversationId() == groupId && m.getUserId() == userId).findFirst().orElseThrow();
    }
    private GroupInvitation invitation(long id) { return store.getInvitations().stream().filter(i -> i.getId() == id).findFirst().orElseThrow(); }
    private UserAccount user(long id) { return store.getUsers().stream().filter(u -> u.getId() == id).findFirst().orElseThrow(); }
    private void login(long id) { assertTrue(auth.login(user(id).getEmail(), MockFixtures.DEMO_PASSWORD, false).isSuccess()); }
    private static List<Long> groupIds(List<Conversation> list) { return list.stream().map(Conversation::getId).toList(); }
    private static List<Long> memberIds(List<ConversationMember> list) { return list.stream().map(ConversationMember::getUserId).toList(); }
}
