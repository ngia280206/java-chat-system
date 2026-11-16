package com.chatsystem.user.controller;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.user.navigation.UserNavigator;
import com.chatsystem.user.navigation.UserRoute;
import com.chatsystem.user.navigation.ViewRequest;
import com.chatsystem.user.ui.DisabledControlSupport;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class UserShellController {
    private final UserSession session;
    private final UserNavigator navigator;
    private Map<UserRoute, Button> navigationButtons;

    @FXML private Button chatsButton;
    @FXML private Button friendsButton;
    @FXML private Button groupsButton;
    @FXML private Button reportsButton;
    @FXML private Button profileButton;
    @FXML private Button settingsButton;
    @FXML private Button helpButton;
    @FXML private StackPane helpWrapper;
    @FXML private Label currentUserName;
    @FXML private Label currentUserInitials;
    @FXML private StackPane contentHost;

    public UserShellController(UserSession session, UserNavigator navigator) {
        this.session = Objects.requireNonNull(session);
        this.navigator = Objects.requireNonNull(navigator);
    }

    @FXML
    private void initialize() {
        navigationButtons = Map.of(UserRoute.CHAT_HOME, chatsButton, UserRoute.FRIENDS, friendsButton,
                UserRoute.GROUP_MANAGEMENT, groupsButton, UserRoute.SPAM_REPORTS, reportsButton,
                UserRoute.PROFILE, profileButton, UserRoute.SETTINGS, settingsButton);
        navigationButtons.forEach((route, button) -> button.setOnAction(event -> navigator.navigate(ViewRequest.of(route))));
        DisabledControlSupport.install(helpButton, helpWrapper,
                "Help and support are unavailable because no support destination is defined.");
        // The shell is created for one authenticated identity and discarded at sign out.
        UserAccount user = Objects.requireNonNull(session.getCurrentUser(), "Authenticated shell requires a user");
        currentUserName.textProperty().bind(user.displayNameProperty());
        currentUserInitials.textProperty().bind(Bindings.createStringBinding(
                () -> Stream.of(user.getDisplayName().trim().split("\\s+"))
                        .filter(part -> !part.isEmpty()).limit(2)
                        .map(part -> part.substring(0, 1).toUpperCase(java.util.Locale.ROOT))
                        .collect(Collectors.joining()), user.displayNameProperty()));
    }

    public void show(ViewRequest request, Parent content) {
        contentHost.getChildren().setAll(content);
        navigationButtons.forEach((route, button) -> {
            button.getStyleClass().removeAll("nav-active", "nav-button");
            button.getStyleClass().add(route == request.route().sidebarRoute() ? "nav-active" : "nav-button");
        });
    }
}
