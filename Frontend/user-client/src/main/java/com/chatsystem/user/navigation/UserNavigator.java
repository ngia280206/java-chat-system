package com.chatsystem.user.navigation;

import com.chatsystem.user.UserApplicationContext;
import com.chatsystem.user.controller.UserShellController;
import com.chatsystem.user.ui.FxmlViewLoader;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.Optional;

public final class UserNavigator {
    private final Stage stage;
    private final UserApplicationContext context;
    private final FxmlViewLoader views;
    private final ReadOnlyObjectWrapper<ViewRequest> currentRequest = new ReadOnlyObjectWrapper<>();
    private Parent shell;
    private UserShellController shellController;
    private Stage assistantStage;

    public UserNavigator(Stage stage, UserApplicationContext context) {
        this.stage = Objects.requireNonNull(stage);
        this.context = Objects.requireNonNull(context);
        this.views = new FxmlViewLoader(context, this);
        context.session().authenticatedProperty().addListener((observable, before, authenticated) -> {
            if (!authenticated) {
                if (assistantStage != null) assistantStage.close();
                assistantStage = null;
                shell = null;
                shellController = null;
            }
            navigate(ViewRequest.of(authenticated ? UserRoute.CHAT_HOME : UserRoute.LOGIN));
        });
    }

    public void start() {
        navigate(ViewRequest.of(context.session().isAuthenticated() ? UserRoute.CHAT_HOME : UserRoute.LOGIN));
    }

    public ViewRequest currentRequest() { return currentRequest.get(); }
    public ReadOnlyObjectProperty<ViewRequest> currentRequestProperty() { return currentRequest.getReadOnlyProperty(); }
    public Optional<Stage> assistantStage() { return Optional.ofNullable(assistantStage); }

    public void navigate(ViewRequest request) {
        Objects.requireNonNull(request);
        var profile = request.route().profile();
        if (profile != UserRoute.WindowProfile.AUTH && !context.session().isAuthenticated()) {
            throw new IllegalStateException("Authentication required for " + request.route());
        }
        if (profile == UserRoute.WindowProfile.ASSISTANT) {
            if (assistantStage == null) {
                assistantStage = new Stage();
                assistantStage.initOwner(stage);
                assistantStage.setTitle("RELAY AI assistant");
            }
            setScene(assistantStage, routeContent(request), profile);
            assistantStage.show();
            return;
        }
        if (profile == UserRoute.WindowProfile.SHELL) {
            if (shell == null) {
                var loaded = views.load("/fxml/shell/user-shell.fxml");
                shell = loaded.root();
                shellController = (UserShellController) loaded.controller();
            }
            shellController.show(request, routeContent(request));
            if (stage.getScene() == null || stage.getScene().getRoot() != shell) setScene(stage, shell, profile);
        } else {
            setScene(stage, routeContent(request), profile);
        }
        currentRequest.set(request);
    }

    private void setScene(Stage target, Parent root, UserRoute.WindowProfile profile) {
        Scene scene = new Scene(root, profile.width(), profile.height());
        scene.getStylesheets().add(views.resource("/css/dark.css").toExternalForm());
        target.setResizable(false);
        target.setScene(scene);
        target.sizeToScene();
    }

    /** Single replacement point for the FXML/controllers implemented in Tasks 7–11. */
    private Parent routeContent(ViewRequest request) {
        Label title = new Label(request.route().title());
        title.getStyleClass().add("page-title");
        StackPane content = new StackPane(title);
        content.getStyleClass().add("app-root");
        content.setUserData(request);
        return content;
    }
}
