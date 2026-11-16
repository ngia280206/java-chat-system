package com.chatsystem.shared.service;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.ProfileVisibility;
import com.chatsystem.shared.validation.ValidationResult;

public interface ProfileService {
    /** Saves all profile fields together for the active session; drafts stay in the caller. */
    ValidationResult<UserAccount> updateProfile(String displayName, String username, String bio,
                                               ProfileVisibility profileVisibility);
}
