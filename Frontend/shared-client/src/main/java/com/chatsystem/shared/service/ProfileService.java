package com.chatsystem.shared.service;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.ProfileVisibility;
import com.chatsystem.shared.validation.ValidationResult;

public interface ProfileService {
    /** Saves all profile fields together for the active session; drafts stay in the caller. */
    ValidationResult<UserAccount> updateProfile(String displayName, String username, String bio,
                                               ProfileVisibility profileVisibility);
    /** V1 demo profile extension with personal details; existing callers remain compatible. */
    default ValidationResult<UserAccount> updateProfile(String displayName, String username, String bio,
            ProfileVisibility visibility, String address, java.time.LocalDate dateOfBirth, String gender) {
        ValidationResult<UserAccount> result = updateProfile(displayName, username, bio, visibility);
        if (result.isSuccess()) result.value().orElseThrow().updatePersonalDetails(address, dateOfBirth, gender);
        return result;
    }
}
