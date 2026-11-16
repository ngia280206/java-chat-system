package com.chatsystem.user.controller.auth;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.user.JavaFxTestSupport;
import com.chatsystem.user.UserApplicationContext;
import com.chatsystem.user.navigation.UserNavigator;
import com.chatsystem.user.navigation.UserRoute;
import com.chatsystem.user.navigation.ViewRequest;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AuthWorkflowControllerTest extends JavaFxTestSupport {
    private static class Workflow implements AutoCloseable {
        final UserApplicationContext context = new UserApplicationContext();
        final Stage stage = new Stage();
        final UserNavigator navigator = new UserNavigator(stage, context);
        Workflow() { navigator.start(); }
        <T> T control(String id, Class<T> type) {
            var node = stage.getScene().getRoot().lookup("#" + id);
            assertNotNull(node, "Missing auth control: " + id);
            return type.cast(node);
        }
        void input(String id, String value) { control(id, TextInputControl.class).setText(value); }
        void click(String id) { control(id, Button.class).fire(); }
        void route(UserRoute route) { assertEquals(route, navigator.currentRequest().route()); }
        String feedback() {
            Label label = control("feedbackLabel", Label.class);
            assertTrue(label.isVisible());
            assertTrue(label.isManaged());
            return label.getText();
        }
        void login(String email, String password) {
            input("emailField", email); input("passwordField", password); click("submitButton");
        }
        void beginReset() {
            click("forgotPasswordButton"); input("emailField", MockFixtures.DEMO_EMAIL); click("submitButton");
            route(UserRoute.VERIFICATION_CODE);
        }
        void code(String code) {
            for (int i = 0; i < 6; i++) input("code" + (i + 1), code.substring(i, i + 1));
        }
        @Override public void close() { stage.close(); }
    }

    private void workflow(Consumer<Workflow> action) throws Exception {
        onFx(() -> { try (var workflow = new Workflow()) { action.accept(workflow); } });
    }

    @Test void loginShowsServiceValidationCredentialAndLockedFeedback() throws Exception {
        workflow(w -> {
            w.click("submitButton");
            assertTrue(w.feedback().contains("valid email"));
            w.input("emailField", MockFixtures.DEMO_EMAIL); w.click("submitButton");
            assertTrue(w.feedback().contains("Password is required"));
            w.login("invalid", "wrong"); assertTrue(w.feedback().contains("valid email"));
            w.login(MockFixtures.DEMO_EMAIL, "wrong"); assertTrue(w.feedback().contains("incorrect"));
            w.login(MockFixtures.LOCKED_EMAIL, MockFixtures.DEMO_PASSWORD);
            assertTrue(w.feedback().contains("locked"));
            assertFalse(w.context.session().isAuthenticated()); w.route(UserRoute.LOGIN);
        });
    }

    @Test void loginRememberEmailSurvivesLogoutWithoutKeepingPasswordOrShell() throws Exception {
        workflow(w -> {
            w.control("rememberEmailCheckBox", CheckBox.class).setSelected(true);
            w.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD);
            w.route(UserRoute.CHAT_HOME); assertTrue(w.context.session().isAuthenticated());
            assertEquals(1540, w.stage.getScene().getWidth()); assertFalse(w.stage.isResizable());
            w.context.auth().logout(); w.route(UserRoute.LOGIN);
            assertEquals(MockFixtures.DEMO_EMAIL, w.control("emailField", TextField.class).getText());
            assertTrue(w.control("rememberEmailCheckBox", CheckBox.class).isSelected());
            assertEquals("", w.control("passwordField", PasswordField.class).getText());
            w.control("rememberEmailCheckBox", CheckBox.class).setSelected(false);
            w.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD);
            w.context.auth().logout();
            assertEquals("", w.control("emailField", TextField.class).getText());
        });
    }

    @Test void registrationShowsAllValidationAndCreatesAnAccountWithoutSigningIn() throws Exception {
        workflow(w -> {
            w.click("registerButton"); w.route(UserRoute.REGISTER); w.click("submitButton");
            assertTrue(w.feedback().contains("Display name"));
            w.input("displayNameField", "New Member"); w.click("submitButton");
            assertTrue(w.feedback().contains("valid email"));
            w.input("emailField", "new.member@example.com"); w.click("submitButton");
            assertTrue(w.feedback().contains("8 characters"));
            w.input("passwordField", "NewPass123!"); w.click("submitButton");
            assertTrue(w.feedback().contains("Passwords must match"));
            w.input("confirmPasswordField", "NewPass123!"); w.click("submitButton");
            assertTrue(w.feedback().contains("terms"));
            w.input("emailField", MockFixtures.DEMO_EMAIL);
            w.input("passwordField", "NewPass123!"); w.input("confirmPasswordField", "NewPass123!");
            w.control("termsCheckBox", CheckBox.class).setSelected(true); w.click("submitButton");
            assertTrue(w.feedback().contains("already registered"));
            w.input("emailField", "new.member@example.com"); w.click("submitButton");
            w.route(UserRoute.LOGIN); assertTrue(w.feedback().contains("Account created"));
            assertFalse(w.context.session().isAuthenticated());
            assertEquals("new.member@example.com", w.control("emailField", TextField.class).getText());
            w.login("new.member@example.com", "NewPass123!"); w.route(UserRoute.CHAT_HOME);
        });
    }

    @Test void resetShowsValidationResendsAndReturnsToLoginWithChangedPassword() throws Exception {
        workflow(w -> {
            w.click("forgotPasswordButton"); w.click("submitButton"); assertTrue(w.feedback().contains("valid email"));
            w.input("emailField", "unknown@example.com"); w.click("submitButton"); assertTrue(w.feedback().contains("No account"));
            w.input("emailField", MockFixtures.DEMO_EMAIL); w.click("submitButton");
            w.route(UserRoute.VERIFICATION_CODE); assertTrue(w.feedback().contains(MockFixtures.RESET_CODE));
            assertTrue(w.control("recipientLabel", Label.class).getText().contains(MockFixtures.DEMO_EMAIL));
            w.code("000000"); w.click("submitButton"); assertTrue(w.feedback().contains("verification code"));
            w.click("resendButton"); assertTrue(w.feedback().contains("resent"));
            w.code(MockFixtures.RESET_CODE); w.click("submitButton"); w.route(UserRoute.RESET_PASSWORD);
            w.input("passwordField", "short"); w.input("confirmPasswordField", "different"); w.click("submitButton");
            assertTrue(w.feedback().contains("8 characters"));
            w.input("passwordField", "ChangedPass123!"); w.click("submitButton");
            assertTrue(w.feedback().contains("Passwords must match"));
            w.input("confirmPasswordField", "ChangedPass123!");
            w.click("submitButton"); w.route(UserRoute.LOGIN); assertTrue(w.feedback().contains("Password reset"));
            w.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD); assertTrue(w.feedback().contains("incorrect"));
            w.login(MockFixtures.DEMO_EMAIL, "ChangedPass123!"); w.route(UserRoute.CHAT_HOME);
        });
    }

    @Test void backLinksCancelResetAndReturnToLogin() throws Exception {
        workflow(w -> {
            w.click("registerButton"); w.click("backButton"); w.route(UserRoute.LOGIN);
            w.click("forgotPasswordButton"); w.click("backButton"); w.route(UserRoute.LOGIN);
            w.beginReset(); w.code(MockFixtures.RESET_CODE); w.click("submitButton");
            w.click("backButton"); w.route(UserRoute.LOGIN);
            assertFalse(w.context.auth().resetPassword("ChangedPass123!", "ChangedPass123!").isSuccess());
            w.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD); w.route(UserRoute.CHAT_HOME);
        });
    }

    @Test void directResetRouteShowsMissingVerificationFeedback() throws Exception {
        workflow(w -> {
            w.navigator.navigate(ViewRequest.of(UserRoute.RESET_PASSWORD));
            w.input("passwordField", "ChangedPass123!"); w.input("confirmPasswordField", "ChangedPass123!");
            w.click("submitButton"); assertTrue(w.feedback().contains("Verify a password reset code first"));
            w.route(UserRoute.RESET_PASSWORD);
        });
    }
}
