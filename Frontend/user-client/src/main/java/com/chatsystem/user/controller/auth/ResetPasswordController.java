package com.chatsystem.user.controller.auth;

import com.chatsystem.shared.service.AuthService;
import com.chatsystem.user.navigation.UserNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class ResetPasswordController {
    private final AuthService auth;
    private final UserNavigator navigator;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label feedbackLabel;

    public ResetPasswordController(AuthService auth, UserNavigator navigator) {
        this.auth = auth;
        this.navigator = navigator;
    }

    @FXML private void submit() {
        var result = auth.resetPassword(passwordField.getText(), confirmPasswordField.getText());
        if (result.isSuccess()) navigator.showLogin(navigator.passwordResetEmail(), "Password reset. Sign in with your new password.");
        else AuthFeedback.errors(feedbackLabel, result);
    }

    @FXML private void back() {
        auth.logout();
        navigator.showLogin(navigator.passwordResetEmail(), "Password reset cancelled. Your password has not changed.");
    }
}
