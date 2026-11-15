package com.chatsystem.user.controller.auth;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.service.AuthService;
import com.chatsystem.user.navigation.UserNavigator;
import com.chatsystem.user.navigation.UserRoute;
import com.chatsystem.user.navigation.ViewRequest;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;

public final class VerificationCodeController {
    private final AuthService auth;
    private final UserNavigator navigator;
    private String email = "";
    @FXML private TextField code1;
    @FXML private TextField code2;
    @FXML private TextField code3;
    @FXML private TextField code4;
    @FXML private TextField code5;
    @FXML private TextField code6;
    @FXML private Label recipientLabel;
    @FXML private Label feedbackLabel;

    public VerificationCodeController(AuthService auth, UserNavigator navigator) {
        this.auth = auth;
        this.navigator = navigator;
    }

    @FXML private void initialize() {
        TextField[] digits = {code1, code2, code3, code4, code5, code6};
        for (int i = 0; i < digits.length; i++) {
            final int index = i;
            digits[i].setTextFormatter(new TextFormatter<String>(change ->
                    change.getControlNewText().matches("[0-9]?") ? change : null));
            digits[i].textProperty().addListener((observable, before, value) -> {
                if (!value.isEmpty() && index < 5) digits[index + 1].requestFocus();
            });
            digits[i].setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.BACK_SPACE && digits[index].getText().isEmpty() && index > 0)
                    digits[index - 1].requestFocus();
                if (event.getCode() == KeyCode.ENTER) submit();
            });
        }
    }

    public void present(String email) {
        this.email = email;
        recipientLabel.setText("Enter the six-digit verification code for " + email + ".");
        AuthFeedback.message(feedbackLabel, "Local demo code: " + MockFixtures.RESET_CODE + ". No email is sent.");
    }

    @FXML private void submit() {
        var result = auth.verifyResetCode(code1.getText() + code2.getText() + code3.getText()
                + code4.getText() + code5.getText() + code6.getText());
        if (result.isSuccess()) navigator.navigate(ViewRequest.of(UserRoute.RESET_PASSWORD));
        else AuthFeedback.errors(feedbackLabel, result);
    }

    @FXML private void resend() {
        var result = auth.beginPasswordReset(email);
        if (result.isSuccess()) {
            for (var field : new TextField[]{code1, code2, code3, code4, code5, code6}) field.clear();
            code1.requestFocus();
            AuthFeedback.message(feedbackLabel, "Demo code resent: " + MockFixtures.RESET_CODE + ". No email is sent.");
        } else AuthFeedback.errors(feedbackLabel, result);
    }
}
