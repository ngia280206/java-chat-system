package com.chatsystem.user;

import com.chatsystem.user.navigation.UserNavigator;
import com.chatsystem.user.ui.FxmlViewLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FxmlSmokeTest extends JavaFxTestSupport {
    @Test void allChatDesignsLoadWithExplicitControllers() throws Exception {
        onFx(() -> {
            var context = new UserApplicationContext();
            assertTrue(context.auth().login(com.chatsystem.shared.mock.MockFixtures.DEMO_EMAIL,
                    com.chatsystem.shared.mock.MockFixtures.DEMO_PASSWORD, false).isSuccess());
            var stage = new Stage();
            try {
                var loader = new FxmlViewLoader(context, new UserNavigator(stage, context));
                String[] views = {"chat-home", "chat-private", "chat-group", "message-search"};
                for (String view : views) assertNotNull(loader.load("/fxml/views/" + view + ".fxml").controller());
                assertNotNull(loader.load("/fxml/dialogs/new-conversation.fxml").controller());
                assertNotNull(loader.load("/fxml/dialogs/ai-assistant-popup.fxml").controller());
                for (String view : new String[]{"friends", "friend-requests", "blocked-users"})
                    assertNotNull(loader.load("/fxml/views/" + view + ".fxml").controller());
                assertNotNull(loader.load("/fxml/dialogs/add-friend.fxml").controller());
                for (String view : new String[]{"group-management", "group-details", "group-invitations"})
                    assertNotNull(loader.load("/fxml/views/" + view + ".fxml").controller());
                assertNotNull(loader.load("/fxml/dialogs/create-group.fxml").controller());
                assertNotNull(loader.load("/fxml/dialogs/invite-members.fxml").controller());
                for (String view : new String[]{"profile", "settings", "change-password", "spam-reports", "report-detail"})
                    assertNotNull(loader.load("/fxml/views/" + view + ".fxml").controller());
                assertNotNull(loader.load("/fxml/dialogs/new-report.fxml").controller());
                var profile = loader.load("/fxml/views/profile.fxml").root();
                assertNotNull(profile.lookup("#profileEmailLabel")); assertNotNull(profile.lookup("#memberSinceLabel")); assertNotNull(profile.lookup("#accountStatusLabel"));
                assertEquals(1, profile.lookupAll(".profile-main-card").size());
                assertEquals(1, profile.lookupAll(".profile-account-card").size());
                assertNotNull(profile.lookup("#profileInitialsLabel"));
                var settings = loader.load("/fxml/views/settings.fxml").root();
                assertNotNull(settings.lookup("#privacySummaryLabel")); assertNotNull(settings.lookup("#securitySummaryLabel"));
                var detail = loader.load("/fxml/views/report-detail.fxml").root();
                assertNotNull(detail.lookup("#reportTargetLabel")); assertNotNull(detail.lookup("#reportProgressLabel")); assertNotNull(detail.lookup("#reportTimelineLabel"));
            } finally { stage.close(); }
        });
    }
    @Test void allAuthDesignsLoadWithExplicitControllersAndClasspathTheme() throws Exception {
        onFx(() -> {
            var context = new UserApplicationContext();
            var stage = new Stage();
            try {
                var loader = new FxmlViewLoader(context, new UserNavigator(stage, context));
                String[] files = {"login", "register", "forgot-password", "verification-code", "reset-password"};
                String[] controllers = {"LoginController", "RegisterController", "ForgotPasswordController",
                        "VerificationCodeController", "ResetPasswordController"};
                for (int i = 0; i < files.length; i++) {
                    var loaded = loader.load("/fxml/auth/" + files[i] + ".fxml");
                    assertEquals(controllers[i], loaded.controller().getClass().getSimpleName());
                    assertEquals(1240, ((Region) loaded.root()).getPrefWidth());
                    assertEquals(780, ((Region) loaded.root()).getPrefHeight());
                    var scene = new Scene(loaded.root(), 1240, 780);
                    scene.getStylesheets().add(loader.resource("/css/dark.css").toExternalForm());
                    loaded.root().applyCss(); loaded.root().layout();
                    assertTrue(loaded.root().getStyleClass().contains("auth-root"));
                    assertEquals(1, loaded.root().lookupAll(".auth-brand").size());
                    assertEquals(1, loaded.root().lookupAll(".auth-panel").size());
                    assertFalse(loaded.root().lookup("#submitButton").isDisabled());
                    ((Button) loaded.root().lookup("#submitButton")).fire();
                    loaded.root().applyCss(); loaded.root().layout();
                    Label feedback = (Label) loaded.root().lookup("#feedbackLabel");
                    assertTrue(feedback.getHeight() >= feedback.prefHeight(feedback.getWidth()) - 1,
                            files[i] + " feedback must fit without clipping");
                    for (var node : loaded.root().lookupAll(".button")) {
                        var bounds = node.localToScene(node.getBoundsInLocal());
                        assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= 780,
                                files[i] + " action must stay inside fixed window: " + node.getId());
                    }
                }
            } finally { stage.close(); }
        });
    }
}
