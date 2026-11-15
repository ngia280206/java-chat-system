package com.chatsystem.user.ui;

import com.chatsystem.user.UserApplicationContext;
import com.chatsystem.user.controller.UserShellController;
import com.chatsystem.user.controller.auth.*;
import com.chatsystem.user.controller.chat.*;
import com.chatsystem.user.controller.social.*;
import com.chatsystem.user.controller.group.*;
import com.chatsystem.user.navigation.UserNavigator;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

/** Classpath resources and explicit controller constructors, shared by all views. */
public final class FxmlViewLoader {
    public record LoadedView(Parent root, Object controller) { }

    private final UserApplicationContext context;
    private final UserNavigator navigator;

    public FxmlViewLoader(UserApplicationContext context, UserNavigator navigator) {
        this.context = Objects.requireNonNull(context);
        this.navigator = Objects.requireNonNull(navigator);
    }

    public URL resource(String path) {
        URL resource = FxmlViewLoader.class.getResource(path);
        if (resource == null) throw new IllegalArgumentException("Missing classpath resource: " + path);
        return resource;
    }

    public LoadedView load(String path) {
        FXMLLoader loader = new FXMLLoader(resource(path));
        loader.setControllerFactory(this::createController);
        try {
            Parent root = loader.load();
            return new LoadedView(root, loader.getController());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load FXML resource: " + path, exception);
        }
    }

    public Object createController(Class<?> type) {
        if (type == UserShellController.class) return new UserShellController(context.session(), navigator);
        if (type == LoginController.class) return new LoginController(context.auth(), navigator);
        if (type == RegisterController.class) return new RegisterController(context.auth(), navigator);
        if (type == ForgotPasswordController.class) return new ForgotPasswordController(context.auth(), navigator);
        if (type == VerificationCodeController.class) return new VerificationCodeController(context.auth(), navigator);
        if (type == ResetPasswordController.class) return new ResetPasswordController(context.auth(), navigator);
        if (type == ChatHomeController.class) return new ChatHomeController(context.chat(), navigator);
        if (type == ChatPrivateController.class) return new ChatPrivateController(context.chat(), navigator);
        if (type == ChatGroupController.class) return new ChatGroupController(context.chat(), navigator);
        if (type == MessageSearchController.class) return new MessageSearchController(context.chat(), navigator);
        if (type == NewConversationDialogController.class) return new NewConversationDialogController(context.chat(), navigator);
        if (type == AiAssistantPopupController.class) return new AiAssistantPopupController(context.assistant());
        if (type == FriendsController.class) return new FriendsController(context.social(), context.chat(), navigator);
        if (type == FriendRequestsController.class) return new FriendRequestsController(context.social(), navigator);
        if (type == BlockedUsersController.class) return new BlockedUsersController(context.social(), navigator);
        if (type == AddFriendDialogController.class) return new AddFriendDialogController(context.social());
        if (type == GroupManagementController.class) return new GroupManagementController(context.groups(), navigator);
        if (type == GroupDetailsController.class) return new GroupDetailsController(context.groups(), context.session(), navigator);
        if (type == GroupInvitationsController.class) return new GroupInvitationsController(context.groups(), navigator);
        if (type == CreateGroupDialogController.class) return new CreateGroupDialogController(context.groups(), navigator);
        if (type == InviteMembersDialogController.class) return new InviteMembersDialogController(context.groups());
        throw new IllegalArgumentException("No explicit controller constructor for " + type.getName());
    }
}
