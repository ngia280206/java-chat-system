package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.UserStatus;
import com.chatsystem.shared.service.AuthService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.shared.validation.ValidationResult;
import javafx.beans.property.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryAuthServiceTest {
    private MockDataStore store;
    private AuthService auth;
    private UserSession session;

    @BeforeEach
    void setUp() {
        store = MockFixtures.createStore();
        auth = new InMemoryAuthService(store);
        session = auth.getSession();
    }

    @Test
    void successfulLoginPublishesSharedIdentityAndHistoryThroughReadOnlySession() {
        List<Boolean> changes = new ArrayList<>();
        session.authenticatedProperty().addListener((o, oldValue, value) -> changes.add(value));
        int logins = store.getLoginEvents().size();
        var result = auth.login("  ALEX.MORGAN@EXAMPLE.COM ", MockFixtures.DEMO_PASSWORD, true);
        assertTrue(result.isSuccess());
        assertSame(store.getUsers().get(0), result.value().orElseThrow());
        assertSame(result.value().orElseThrow(), session.getCurrentUser());
        assertTrue(session.isAuthenticated());
        assertEquals(List.of(true), changes);
        assertEquals(logins + 1, store.getLoginEvents().size());
        assertEquals(1, store.getLoginEvents().get(logins).userId());
        assertFalse(session.currentUserProperty() instanceof Property<?>);
        assertFalse(session.authenticatedProperty() instanceof Property<?>);
        assertFalse(session.rememberedEmailProperty() instanceof Property<?>);
    }

    @Test
    void invalidCredentialsLeaveSessionRememberedEmailAndHistoryUntouched() {
        int logins = store.getLoginEvents().size();
        assertGlobalFailure(auth.login(MockFixtures.DEMO_EMAIL, "wrong", true));
        assertGlobalFailure(auth.login("unknown@example.com", MockFixtures.DEMO_PASSWORD, true));
        assertFalse(session.isAuthenticated());
        assertNull(session.getCurrentUser());
        assertEquals("", session.getRememberedEmail());
        assertEquals(logins, store.getLoginEvents().size());
    }

    @Test
    void missingLoginFieldsReturnSpecificErrors() {
        assertFields(auth.login(null, " ", false), "email", "password");
        assertFields(auth.login("invalid", MockFixtures.DEMO_PASSWORD, false), "email");
    }

    @Test
    void lockedAndDeletedAccountsCannotLogin() {
        assertGlobalFailure(auth.login(MockFixtures.LOCKED_EMAIL, MockFixtures.DEMO_PASSWORD, true));
        store.getUsers().get(0).setStatus(UserStatus.DELETED);
        assertGlobalFailure(auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, true));
        assertFalse(session.isAuthenticated());
    }

    @Test
    void failedLoginDoesNotReplaceAnExistingSession() {
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, true);
        UserAccount current = session.getCurrentUser();
        assertGlobalFailure(auth.login(MockFixtures.LOCKED_EMAIL, MockFixtures.DEMO_PASSWORD, false));
        assertSame(current, session.getCurrentUser());
        assertEquals(MockFixtures.DEMO_EMAIL, session.getRememberedEmail());
    }

    @Test
    void rememberEmailSurvivesLogoutOnlyInThisAuthInstanceAndUncheckedLoginClearsIt() {
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, true);
        auth.logout();
        assertFalse(session.isAuthenticated());
        assertNull(session.getCurrentUser());
        assertEquals(MockFixtures.DEMO_EMAIL, session.getRememberedEmail());
        assertEquals("", new InMemoryAuthService(store).getSession().getRememberedEmail());
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        auth.logout();
        assertEquals("", session.getRememberedEmail());
    }

    @Test
    void registrationCreatesUniqueAccountAndSettingsWithoutAuthenticating() {
        int count = store.getUsers().size();
        var result = auth.register("  New Person  ", " NEW@EXAMPLE.COM ", "NewPass123!", "NewPass123!", true);
        assertTrue(result.isSuccess());
        UserAccount user = result.value().orElseThrow();
        assertEquals(count + 1, store.getUsers().size());
        assertEquals("new@example.com", user.getEmail());
        assertEquals("New Person", user.getDisplayName());
        assertFalse(user.getUsername().isBlank());
        assertEquals(store.getUsers().size(), store.getUsers().stream().map(UserAccount::getId).distinct().count());
        assertTrue(store.getSettings().stream().anyMatch(s -> s.getUserId() == user.getId()));
        assertFalse(session.isAuthenticated());
        assertTrue(auth.login("new@example.com", "NewPass123!", false).isSuccess());
        assertFalse(new InMemoryAuthService(MockFixtures.createStore()).login("new@example.com", "NewPass123!", false).isSuccess());
    }

    @Test
    void invalidRegistrationAndDuplicateEmailDoNotCreatePartialState() {
        int users = store.getUsers().size();
        int settings = store.getSettings().size();
        assertFields(auth.register(" ", "bad", "short", "different", false),
                "displayName", "email", "password", "confirmPassword", "termsAccepted");
        assertFields(auth.register("New", " ALEX.MORGAN@EXAMPLE.COM ", "NewPass123!", "NewPass123!", true), "email");
        assertEquals(users, store.getUsers().size());
        assertEquals(settings, store.getSettings().size());
        assertTrue(auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false).isSuccess());
    }

    @Test
    void passwordStrengthRequiresEightCharactersOneNumberAndOneSpecialCharacter() {
        for (String password : List.of("12345678", "abcdefgh!", "Abcd12!", "Abcdef12")) {
            assertFields(auth.register("New", "new@example.com", password, password, true), "password");
        }
    }

    @Test
    void resetRequiresBeginAndCorrectCodeAndConsumesAuthorizationOnSuccess() {
        assertGlobalFailure(auth.verifyResetCode(MockFixtures.RESET_CODE));
        assertGlobalFailure(auth.resetPassword("ResetPass123!", "ResetPass123!"));
        assertTrue(auth.beginPasswordReset(MockFixtures.DEMO_EMAIL).isSuccess());
        assertGlobalFailure(auth.resetPassword("ResetPass123!", "ResetPass123!"));
        assertFields(auth.verifyResetCode("000000"), "code");
        assertTrue(auth.verifyResetCode(MockFixtures.RESET_CODE).isSuccess());
        assertFields(auth.resetPassword("short", "different"), "newPassword", "confirmPassword");
        assertTrue(auth.resetPassword("ResetPass123!", "ResetPass123!").isSuccess());
        assertGlobalFailure(auth.resetPassword("OtherPass123!", "OtherPass123!"));
        assertFalse(session.isAuthenticated());
        assertFalse(auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false).isSuccess());
        assertTrue(auth.login(MockFixtures.DEMO_EMAIL, "ResetPass123!", false).isSuccess());
    }

    @Test
    void resetRejectsUnknownLockedAndInvalidEmailWithoutChangingCredentials() {
        assertFields(auth.beginPasswordReset(null), "email");
        assertFields(auth.beginPasswordReset("bad"), "email");
        assertFields(auth.beginPasswordReset("missing@example.com"), "email");
        assertGlobalFailure(auth.beginPasswordReset(MockFixtures.LOCKED_EMAIL));
        assertGlobalFailure(auth.verifyResetCode(MockFixtures.RESET_CODE));
        assertTrue(auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false).isSuccess());
    }

    @Test
    void restartingResetRequiresVerificationForTheNewTarget() {
        auth.beginPasswordReset(MockFixtures.DEMO_EMAIL);
        auth.verifyResetCode(MockFixtures.RESET_CODE);
        assertTrue(auth.beginPasswordReset("mia.tran@example.com").isSuccess());
        assertGlobalFailure(auth.resetPassword("ResetPass123!", "ResetPass123!"));
        auth.verifyResetCode(MockFixtures.RESET_CODE);
        assertTrue(auth.resetPassword("ResetPass123!", "ResetPass123!").isSuccess());
        assertTrue(auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false).isSuccess());
        assertTrue(auth.login("mia.tran@example.com", "ResetPass123!", false).isSuccess());
    }

    @Test
    void logoutClearsPendingResetAndAuthentication() {
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, true);
        auth.beginPasswordReset(MockFixtures.DEMO_EMAIL);
        auth.verifyResetCode(MockFixtures.RESET_CODE);
        auth.logout();
        assertNull(session.getCurrentUser());
        assertFalse(session.isAuthenticated());
        assertGlobalFailure(auth.resetPassword("ResetPass123!", "ResetPass123!"));
        assertGlobalFailure(auth.verifyResetCode(MockFixtures.RESET_CODE));
    }

    @Test
    void passwordChangeValidatesCurrentStrengthConfirmationAndReuseWithoutMutation() {
        assertGlobalFailure(auth.changePassword(MockFixtures.DEMO_PASSWORD, "NewPass123!", "NewPass123!"));
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        assertFields(auth.changePassword("wrong", "NewPass123!", "NewPass123!"), "currentPassword");
        assertFields(auth.changePassword(MockFixtures.DEMO_PASSWORD, "short", "different"), "newPassword", "confirmPassword");
        assertFields(auth.changePassword(MockFixtures.DEMO_PASSWORD, MockFixtures.DEMO_PASSWORD, MockFixtures.DEMO_PASSWORD), "newPassword");
        assertTrue(auth.changePassword(MockFixtures.DEMO_PASSWORD, "NewPass123!", "NewPass123!").isSuccess());
        assertTrue(session.isAuthenticated());
        auth.logout();
        assertFalse(auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false).isSuccess());
        assertTrue(auth.login(MockFixtures.DEMO_EMAIL, "NewPass123!", false).isSuccess());
    }

    @Test
    void resetRejectsCurrentPasswordAndAccountLockedAfterVerification() {
        auth.beginPasswordReset(MockFixtures.DEMO_EMAIL);
        auth.verifyResetCode(MockFixtures.RESET_CODE);
        assertFields(auth.resetPassword(MockFixtures.DEMO_PASSWORD, MockFixtures.DEMO_PASSWORD), "newPassword");
        store.getUsers().get(0).setStatus(UserStatus.LOCKED);
        assertGlobalFailure(auth.resetPassword("NewPass123!", "NewPass123!"));
        store.getUsers().get(0).setStatus(UserStatus.ACTIVE);
        assertTrue(auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false).isSuccess());
    }

    @Test
    void cancellingStagedPasswordInputWithoutCallingMutationKeepsCredential() {
        auth.beginPasswordReset(MockFixtures.DEMO_EMAIL);
        auth.verifyResetCode(MockFixtures.RESET_CODE);
        // The controller discards its local draft on cancellation; no reset/change call occurs.
        auth.logout();
        assertTrue(auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false).isSuccess());
    }

    private static void assertFields(ValidationResult<?> result, String... fields) {
        assertFalse(result.isSuccess());
        assertTrue(result.value().isEmpty());
        assertEquals(List.of(fields), result.errors().stream().map(e -> e.field()).toList());
    }

    private static void assertGlobalFailure(ValidationResult<?> result) {
        assertFalse(result.isSuccess());
        assertTrue(result.value().isEmpty());
        assertTrue(result.errors().stream().anyMatch(e -> e.isGlobal()));
    }
}
