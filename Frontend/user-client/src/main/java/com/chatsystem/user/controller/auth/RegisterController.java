package com.chatsystem.user.controller.auth;

import com.chatsystem.shared.service.AuthService;
import com.chatsystem.user.navigation.UserNavigator;
import com.chatsystem.user.navigation.UserRoute;
import com.chatsystem.user.navigation.ViewRequest;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class RegisterController {
    private final AuthService auth;
    private final UserNavigator navigator;
    @FXML private TextField displayNameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private CheckBox termsCheckBox;
    @FXML private Label feedbackLabel;

    public RegisterController(AuthService auth, UserNavigator navigator) {
        this.auth = auth;
        this.navigator = navigator;
    }

    @FXML private void submit() {
        var result = auth.register(displayNameField.getText(), emailField.getText(), passwordField.getText(),
                confirmPasswordField.getText(), termsCheckBox.isSelected());
        if (result.isSuccess()) navigator.showLogin(result.value().orElseThrow().getEmail(), "Account created. Sign in to start your conversations.");
        else AuthFeedback.errors(feedbackLabel, result);
    }

    @FXML private void back() { navigator.navigate(ViewRequest.of(UserRoute.LOGIN)); }
}
