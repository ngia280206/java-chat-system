package com.chatsystem.user.controller.account;

import com.chatsystem.shared.model.status.ProfileVisibility;
import com.chatsystem.shared.service.ProfileService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.user.ui.DisabledControlSupport;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

public final class ProfileController {

    private final ProfileService profiles;
    private final UserSession session;
    @FXML
    private TextField displayNameField, usernameField, bioField, addressField;
    @FXML
    private DatePicker birthPicker;
    @FXML
    private ChoiceBox<String> genderChoice;
    @FXML
    private ChoiceBox<ProfileVisibility> visibilityChoice;
    @FXML
    private Label accountFeedbackLabel, profileEmailLabel, memberSinceLabel, accountStatusLabel, profileInitialsLabel, profileDisplayNameLabel, profileUsernameLabel;
    @FXML
    private Button previewButton, avatarButton;
    @FXML
    private StackPane previewWrapper, avatarWrapper;

    public ProfileController(ProfileService p, UserSession s) {
        profiles = p;
        session = s;
    }

    @FXML
    private void initialize() {
        var u = session.getCurrentUser();
        displayNameField.setText(u.getDisplayName());
        usernameField.setText(u.getUsername());
        bioField.setText(u.getBio());
        addressField.setText(u.getAddress());
        birthPicker.setValue(u.getDateOfBirth());
        genderChoice.getItems().setAll("Not specified", "Male", "Female", "Other");
        genderChoice.setValue(u.getGender());
        profileEmailLabel.setText(u.getEmail());
        memberSinceLabel.setText(u.getCreatedAt().toString());
        accountStatusLabel.setText(u.getStatus().toString());
        profileDisplayNameLabel.setText(u.getDisplayName());
        profileUsernameLabel.setText("@" + u.getUsername());
        profileInitialsLabel.setText(initials(u.getDisplayName()));
        visibilityChoice.getItems().setAll(ProfileVisibility.values());
        visibilityChoice.setValue(u.getProfileVisibility());
        DisabledControlSupport.install(previewButton, previewWrapper, "Public profile preview is unavailable because no public profile screen is supplied.");
        DisabledControlSupport.install(avatarButton, avatarWrapper, "Avatar selection is unavailable in the frontend-only demo.");
    }

    @FXML
    private void save() {
        var r = profiles.updateProfile(displayNameField.getText(), usernameField.getText(), bioField.getText(), visibilityChoice.getValue(), addressField.getText(), birthPicker.getValue(), genderChoice.getValue());
        if (r.isSuccess()) {
            var u = r.value().orElseThrow();
            profileDisplayNameLabel.setText(u.getDisplayName());
            profileUsernameLabel.setText("@" + u.getUsername());
            profileInitialsLabel.setText(initials(u.getDisplayName()));
        }
        accountFeedbackLabel.setText(r.isSuccess() ? "Profile saved" : r.errors().get(0).message());
    }

    private static String initials(String name) {
        return java.util.Arrays.stream(name.strip().split("\\s+")).filter(s -> !s.isBlank()).limit(2).map(s -> s.substring(0, 1).toUpperCase()).collect(java.util.stream.Collectors.joining());
    }
}
