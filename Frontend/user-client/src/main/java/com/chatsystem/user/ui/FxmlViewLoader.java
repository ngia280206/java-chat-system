package com.chatsystem.user.ui;

import com.chatsystem.user.UserApplicationContext;
import com.chatsystem.user.controller.UserShellController;
import com.chatsystem.user.controller.auth.*;
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
        throw new IllegalArgumentException("No explicit controller constructor for " + type.getName());
    }
}
