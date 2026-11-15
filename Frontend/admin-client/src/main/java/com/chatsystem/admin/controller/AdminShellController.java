package com.chatsystem.admin.controller;

import com.chatsystem.admin.navigation.*; import com.chatsystem.admin.ui.DisabledControlSupport;
import javafx.fxml.FXML; import javafx.scene.Parent; import javafx.scene.control.Button; import javafx.scene.layout.StackPane;
import java.util.*;
public final class AdminShellController {
    private final AdminNavigator navigator; private Map<AdminRoute,Button> buttons;
    @FXML private Button dashboardButton,usersButton,groupsButton,moderationButton,settingsButton,helpButton;
    @FXML private StackPane settingsWrapper,helpWrapper,contentHost;
    public AdminShellController(AdminNavigator navigator){this.navigator=Objects.requireNonNull(navigator);}
    @FXML private void initialize(){
        buttons=Map.of(AdminRoute.DASHBOARD,dashboardButton,AdminRoute.USERS,usersButton,AdminRoute.GROUPS,groupsButton,AdminRoute.MODERATION,moderationButton);
        buttons.forEach((r,b)->b.setOnAction(e->navigator.navigate(ViewRequest.of(r))));
        DisabledControlSupport.install(settingsButton,settingsWrapper,"Admin settings are unavailable because this demo has no admin settings screen.");
        DisabledControlSupport.install(helpButton,helpWrapper,"Help and support are unavailable because no support destination is defined.");
    }
    public void show(ViewRequest request, Parent content){ contentHost.getChildren().setAll(content); buttons.forEach((r,b)->{b.getStyleClass().removeAll("nav-active","nav-button");b.getStyleClass().add(r==request.route().sidebarRoute()?"nav-active":"nav-button");}); }
}
