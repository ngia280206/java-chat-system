package com.chatsystem.user;

import com.chatsystem.user.navigation.UserNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

public final class UserMain extends Application {
    @Override
    public void start(Stage stage) {
        var context = new UserApplicationContext();
        var navigator = new UserNavigator(stage, context);
        stage.setTitle("RELAY Messenger");
        navigator.start();
        stage.show();
    }

    public static void main(String[] args) { launch(args); }
}
