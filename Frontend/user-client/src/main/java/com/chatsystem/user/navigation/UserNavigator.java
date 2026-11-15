package com.chatsystem.user.navigation;

import com.chatsystem.user.UserApplicationContext;
import com.chatsystem.user.controller.UserShellController;
import com.chatsystem.user.controller.auth.LoginController;
import com.chatsystem.user.controller.auth.VerificationCodeController;
import com.chatsystem.user.controller.chat.*;
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
    private String passwordResetEmail = "";
    private String loginEmail;
    private String loginMessage;

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

    public String passwordResetEmail() { return passwordResetEmail; }

    public void showVerification(String email) {
        passwordResetEmail = Objects.requireNonNull(email);
        navigate(ViewRequest.of(UserRoute.VERIFICATION_CODE));
    }

    public void showLogin(String email, String message) {
        loginEmail = Objects.requireNonNull(email);
        loginMessage = Objects.requireNonNull(message);
        navigate(ViewRequest.of(UserRoute.LOGIN));
    }

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
        String authPath = switch (request.route()) {
            case LOGIN -> "login";
            case REGISTER -> "register";
            case FORGOT_PASSWORD -> "forgot-password";
            case VERIFICATION_CODE -> "verification-code";
            case RESET_PASSWORD -> "reset-password";
            default -> null;
        };
        if (authPath != null) {
            var loaded = views.load("/fxml/auth/" + authPath + ".fxml");
            if (loaded.controller() instanceof LoginController login && loginMessage != null) {
                login.present(loginEmail, loginMessage);
                loginEmail = null;
                loginMessage = null;
            }
            if (loaded.controller() instanceof VerificationCodeController verification && !passwordResetEmail.isBlank())
                verification.present(passwordResetEmail);
            if (request.route() == UserRoute.LOGIN) passwordResetEmail = "";
            loaded.root().setUserData(request);
            return loaded.root();
        }
        String viewPath = switch (request.route()) {
            case CHAT_HOME -> "chat-home";
            case CHAT_PRIVATE -> "chat-private";
            case CHAT_GROUP -> "chat-group";
            case MESSAGE_SEARCH -> "message-search";
            case FRIENDS -> "friends";
            case FRIEND_REQUESTS -> "friend-requests";
            case BLOCKED_USERS -> "blocked-users";
            default -> null;
        };
        if (request.route() == UserRoute.AI_ASSISTANT) {
            var loaded = views.load("/fxml/dialogs/ai-assistant-popup.fxml");
            ((AiAssistantPopupController) loaded.controller()).present(request.entityId().orElseThrow().value());
            loaded.root().setUserData(request);
            return loaded.root();
        }
        if (viewPath != null) {
            var loaded = views.load("/fxml/views/" + viewPath + ".fxml");
            long id = request.entityId().map(ViewRequest.EntityId::value).orElse(0L);
            if (loaded.controller() instanceof ChatPrivateController controller) controller.present(id);
            if (loaded.controller() instanceof ChatGroupController controller) controller.present(id);
            if (loaded.controller() instanceof MessageSearchController controller) controller.present(id);
            loaded.root().setUserData(request);
            return loaded.root();
        }
        Label title = new Label(request.route().title());
        title.getStyleClass().add("page-title");
        StackPane content = new StackPane(title);
        content.getStyleClass().add("app-root");
        content.setUserData(request);
        return content;
    }
}
