package com.chatsystem.user.controller.auth;

import com.chatsystem.shared.validation.ValidationResult;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import java.util.stream.Collectors;

/**
 * Presents service feedback without duplicating account policy in the views.
 */
final class AuthFeedback {

    private AuthFeedback() {
    }

    static void errors(Label label, ValidationResult<?> result) {
        label.setText(result.errors().get(0).message());
        label.setTooltip(new Tooltip(result.errors().stream().map(error -> error.message()).collect(Collectors.joining("\n"))));
        label.setStyle("-fx-text-fill: #FF9B9B;");
    }

    static void message(Label label, String message) {
        label.setText(message);
        label.setTooltip(null);
        label.setStyle("");
    }
}
