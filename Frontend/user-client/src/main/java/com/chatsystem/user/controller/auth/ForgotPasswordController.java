package com.chatsystem.user.controller.auth;

import com.chatsystem.shared.service.AuthService;
import com.chatsystem.user.navigation.UserNavigator;
import com.chatsystem.user.navigation.UserRoute;
import com.chatsystem.user.navigation.ViewRequest;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class ForgotPasswordController {

    private final AuthService auth;
    private final UserNavigator navigator;
    @FXML
    private TextField emailField;
    @FXML
    private Label feedbackLabel, emailPreviewLabel;

    public ForgotPasswordController(AuthService auth, UserNavigator navigator) {
        this.auth = auth;
        this.navigator = navigator;
    }

    @FXML
    private void submit() {
        var result = auth.beginPasswordReset(emailField.getText());
        if (result.isSuccess()) {
            navigator.showVerification(emailField.getText().strip()); 
        }else {
            AuthFeedback.errors(feedbackLabel, result);
        }
    }

    @FXML
    private void showEmailPreview() {
        emailPreviewLabel.setText("DESIGN PREVIEW — A generated password would be emailed. No email has been sent.");
    }

    @FXML
    private void back() {
        auth.logout();
        navigator.navigate(ViewRequest.of(UserRoute.LOGIN));
    }
}
