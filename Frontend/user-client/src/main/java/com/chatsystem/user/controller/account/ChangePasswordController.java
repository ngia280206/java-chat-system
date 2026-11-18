package com.chatsystem.user.controller.account;

import com.chatsystem.shared.service.AuthService;
import com.chatsystem.user.navigation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class ChangePasswordController {

    private final AuthService auth;
    private final UserNavigator nav;
    @FXML
    private PasswordField currentPasswordField, newPasswordField, confirmPasswordField;
    @FXML
    private Label accountFeedbackLabel;

    public ChangePasswordController(AuthService a, UserNavigator n) {
        auth = a;
        nav = n;
    }

    @FXML
    private void update() {
        var r = auth.changePassword(currentPasswordField.getText(), newPasswordField.getText(), confirmPasswordField.getText());
        accountFeedbackLabel.setText(r.isSuccess() ? "Password updated" : r.errors().get(0).message());
        if (r.isSuccess()) {
            currentPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
        }
    }

    @FXML
    private void back() {
        nav.navigate(ViewRequest.of(UserRoute.SETTINGS));
    }
}
