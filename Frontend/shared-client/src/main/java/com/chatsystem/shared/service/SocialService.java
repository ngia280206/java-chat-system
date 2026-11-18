package com.chatsystem.shared.service;

import com.chatsystem.shared.model.FriendRequest;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.validation.ValidationResult;
import javafx.collections.ObservableList;

/** Current active account's read-only live relationships. Resolved requests remain in history. */
public interface SocialService {
    ObservableList<UserAccount> getFriends();
    ObservableList<FriendRequest> getIncomingRequests();
    ObservableList<FriendRequest> getOutgoingRequests();
    ObservableList<UserAccount> getBlockedUsers();
    /** Discoverable directory excludes self and both directions of blocking. */
    ObservableList<UserAccount> getDiscoverableUsers();
    ValidationResult<FriendRequest> sendRequest(long userId);
    ValidationResult<Void> acceptRequest(long requestId);
    ValidationResult<Void> rejectRequest(long requestId);
    ValidationResult<Void> cancelRequest(long requestId);
    ValidationResult<Void> removeFriend(long userId);
    ValidationResult<Void> blockUser(long userId);
    ValidationResult<Void> unblockUser(long userId);
}
