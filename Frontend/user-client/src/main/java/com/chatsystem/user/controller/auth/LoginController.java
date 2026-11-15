package com.chatsystem.user.controller.auth;

import com.chatsystem.shared.service.AuthService;
import com.chatsystem.user.navigation.UserNavigator;
import com.chatsystem.user.navigation.UserRoute;
import com.chatsystem.user.navigation.ViewRequest;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class LoginController {
    private final AuthService auth;
    private final UserNavigator navigator;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberEmailCheckBox;
    @FXML private Label feedbackLabel;

    public LoginController(AuthService auth, UserNavigator navigator) {
        this.auth = auth;
        this.navigator = navigator;
    }

    @FXML private void initialize() {
        emailField.setText(auth.getSession().getRememberedEmail());
        rememberEmailCheckBox.setSelected(!emailField.getText().isBlank());
    }

    public void present(String email, String message) {
        emailField.setText(email);
        AuthFeedback.message(feedbackLabel, message);
    }

    @FXML private void submit() {
        var result = auth.login(emailField.getText(), passwordField.getText(), rememberEmailCheckBox.isSelected());
        if (!result.isSuccess()) AuthFeedback.errors(feedbackLabel, result);
        // UserNavigator observes the authenticated session and opens its persistent shell.
    }

    @FXML private void forgotPassword() { navigator.navigate(ViewRequest.of(UserRoute.FORGOT_PASSWORD)); }
    @FXML private void register() { navigator.navigate(ViewRequest.of(UserRoute.REGISTER)); }
}
