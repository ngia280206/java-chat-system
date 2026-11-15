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
