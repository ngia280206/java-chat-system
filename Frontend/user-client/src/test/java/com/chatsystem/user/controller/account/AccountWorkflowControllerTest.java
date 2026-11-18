package com.chatsystem.user.controller.account;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.user.*;
import com.chatsystem.user.navigation.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountWorkflowControllerTest extends JavaFxTestSupport {

    @Test
    void profileSettingsPasswordAndLogoutUseSharedSessionState() throws Exception {
        onFx(() -> {
            var c = new UserApplicationContext();
            c.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
            var s = new Stage();
            try {
                var n = new UserNavigator(s, c);
                n.start();
                n.navigate(ViewRequest.of(UserRoute.PROFILE));
                text(s, "displayNameField").setText("Alex Updated");
                text(s, "usernameField").setText("alexupdated");
                text(s, "bioField").setText("Updated bio");
                click(s, "saveProfileButton");
                assertEquals("Alex Updated", c.session().getCurrentUser().getDisplayName());
                assertTrue(control(s, "previewButton", Button.class).isDisabled());
                assertFalse(control(s, "previewWrapper", StackPane.class).getAccessibleText().isBlank());
                n.navigate(ViewRequest.of(UserRoute.SETTINGS));
                control(s, "notificationCheckBox", CheckBox.class).setSelected(false);
                control(s, "soundCheckBox", CheckBox.class).setSelected(true);
                click(s, "savePreferencesButton");
                assertFalse(c.settings().getSettings().value().orElseThrow().isDesktopNotifications());
                click(s, "changePasswordButton");
                text(s, "currentPasswordField").setText(MockFixtures.DEMO_PASSWORD);
                text(s, "newPasswordField").setText("ChangedPass123!");
                text(s, "confirmPasswordField").setText("ChangedPass123!");
                click(s, "updatePasswordButton");
                assertTrue(label(s, "accountFeedbackLabel").getText().contains("updated"));
                n.navigate(ViewRequest.of(UserRoute.SETTINGS));
                click(s, "blockedAccountsButton");
                assertEquals(UserRoute.BLOCKED_USERS, n.currentRequest().route());
                n.navigate(ViewRequest.of(UserRoute.SETTINGS));
                click(s, "signOutButton");
                assertFalse(c.session().isAuthenticated());
                assertEquals(UserRoute.LOGIN, n.currentRequest().route());
            } finally {
                s.close();
            }
        });
    }

    private static void click(Stage s, String id) {
        control(s, id, Button.class).fire();
    }

    private static TextInputControl text(Stage s, String id) {
        return control(s, id, TextInputControl.class);
    }

    private static Label label(Stage s, String id) {
        return control(s, id, Label.class);
    }

    private static <T extends Node> T control(Stage s, String id, Class<T> type) {
        Node n = s.getScene().getRoot().lookup("#" + id);
        assertNotNull(n, id);
        return type.cast(n);
    }
}
