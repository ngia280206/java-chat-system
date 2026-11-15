package com.chatsystem.admin;

import com.chatsystem.admin.navigation.AdminNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

public final class AdminMain extends Application {
    @Override public void start(Stage stage) {
        var context = new AdminApplicationContext(); var navigator = new AdminNavigator(stage, context);
        stage.setTitle("RELAY Admin Console"); navigator.start(); stage.show();
    }
    public static void main(String[] args) { launch(args); }
}
