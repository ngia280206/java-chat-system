package com.chatsystem.shared.service;

import com.chatsystem.shared.model.Conversation;
import com.chatsystem.shared.model.ConversationMember;
import com.chatsystem.shared.model.GroupInvitation;
import com.chatsystem.shared.model.status.GroupRole;
import com.chatsystem.shared.validation.ValidationResult;
import javafx.collections.ObservableList;

/** Current active account's read-only live groups, pending invitations and current memberships. */
public interface GroupService {
    ObservableList<Conversation> getGroups();
    ObservableList<GroupInvitation> getInvitations();
    /** Empty when the signed-in account is not a current member of this group. */
    ObservableList<ConversationMember> getMembers(long groupId);
    ValidationResult<Conversation> createGroup(String name, String description);
    ValidationResult<Void> updateGroup(long groupId, String name, String description);
    ValidationResult<GroupInvitation> inviteMember(long groupId, long userId);
    ValidationResult<Void> acceptInvitation(long invitationId);
    ValidationResult<Void> rejectInvitation(long invitationId);
    ValidationResult<Void> removeMember(long groupId, long userId);
    ValidationResult<Void> leaveGroup(long groupId);
    ValidationResult<Void> changeRole(long groupId, long userId, GroupRole role);
}
