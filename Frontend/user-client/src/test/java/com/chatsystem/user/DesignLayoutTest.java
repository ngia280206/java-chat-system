package com.chatsystem.user;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.user.navigation.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesignLayoutTest extends JavaFxTestSupport {
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RELAY_CAPTURE", matches="true")
    void captureReferenceReviewScreens() throws Exception {
        onFx(() -> {
            var context = new UserApplicationContext(); var stage = new Stage();
            try {
                var nav = new UserNavigator(stage,context);
                context.auth().login(MockFixtures.DEMO_EMAIL,MockFixtures.DEMO_PASSWORD,false);
                for (var route : UserRoute.values()) {
                    if (route.profile()!=UserRoute.WindowProfile.SHELL) continue;
                    ViewRequest request = switch(route) {
                        case CHAT_PRIVATE, MESSAGE_SEARCH -> ViewRequest.conversation(route,10);
                        case CHAT_GROUP, GROUP_DETAILS -> ViewRequest.conversation(route,20);
                        case REPORT_DETAIL -> ViewRequest.report(501);
                        default -> ViewRequest.of(route);
                    };
                    nav.navigate(request); snapshot(stage.getScene().getRoot(),route.name());
                }
                nav.showAssistant(10,"Can we review the design together?");
                snapshot(nav.assistantStage().orElseThrow().getScene().getRoot(),"AI_ASSISTANT");
                var report=nav.showDialog("/fxml/dialogs/new-report.fxml","New report");
                snapshot(report.getScene().getRoot(),"NEW_REPORT");report.close();
                nav.assistantStage().orElseThrow().close();
                context.auth().logout();
                for(var route:new UserRoute[]{UserRoute.LOGIN,UserRoute.REGISTER,UserRoute.FORGOT_PASSWORD,
                        UserRoute.VERIFICATION_CODE,UserRoute.RESET_PASSWORD}) {
                    nav.navigate(ViewRequest.of(route));snapshot(stage.getScene().getRoot(),route.name());
                }
            } finally { javafx.stage.Window.getWindows().stream().toList().forEach(javafx.stage.Window::hide); stage.close(); }
        });
    }

    private static void snapshot(Parent root,String name) {
        root.applyCss();root.layout();
        var image=root.snapshot(null,null);int w=(int)image.getWidth(),h=(int)image.getHeight();
        var pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);
        var output=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0,0,w,h,pixels,0,w);
        var dir=java.nio.file.Path.of("build/ui-snapshots");
        try {
            java.nio.file.Files.createDirectories(dir);
            javax.imageio.ImageIO.write(output,"png",dir.resolve(name.toLowerCase()+".png").toFile());
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    @Test void pagesKeepReferenceInsetsAndCardSurfaces() throws Exception {
        onFx(() -> {
            var context = new UserApplicationContext();
            var stage = new Stage();
            try {
                var nav = new UserNavigator(stage, context);
                context.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
                for (var route : new UserRoute[]{UserRoute.FRIENDS, UserRoute.FRIEND_REQUESTS,
                        UserRoute.BLOCKED_USERS, UserRoute.GROUP_MANAGEMENT, UserRoute.GROUP_INVITATIONS,
                        UserRoute.SETTINGS, UserRoute.CHANGE_PASSWORD, UserRoute.SPAM_REPORTS, UserRoute.PROFILE}) {
                    nav.navigate(ViewRequest.of(route));
                    Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                    Node title = root.lookup(".page-title");
                    assertTrue(title.localToScene(title.getBoundsInLocal()).getMinX() >= 275, route + " needs page inset");
                    assertFalse(root.lookupAll(".card").isEmpty(), route + " needs its reference cards");
                    for (Node node : root.lookupAll(".button")) {
                        if (!node.isVisible() || !node.isManaged()) continue;
                        var bounds = node.localToScene(node.getBoundsInLocal());
                        assertTrue(bounds.getMaxX() <= 1541, route + " clips " + ((Button) node).getText());
                    }
                }
                Button inactive = (Button) stage.getScene().lookup("#friendsButton");
                assertEquals(Color.TRANSPARENT, inactive.getBackground().getFills().get(0).getFill());
            } finally { stage.close(); }
        });
    }

    @Test void chatKeepsConversationRailBesideWorkspaceAndComposer() throws Exception {
        onFx(() -> {
            var context = new UserApplicationContext(); var stage = new Stage();
            try {
                var nav = new UserNavigator(stage, context);
                context.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
                nav.navigate(ViewRequest.of(UserRoute.CHAT_HOME));
                Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                var list = (ListView<?>) root.lookup("#conversationList");
                assertTrue(list.getWidth() <= 320, "Conversation list must stay in the left rail");
                assertNotNull(root.lookup(".empty-state"));
                nav.navigate(ViewRequest.conversation(UserRoute.CHAT_GROUP, 20));
                root.applyCss(); root.layout();
                Region composer = (Region) root.lookup(".chat-composer");
                assertTrue(composer.localToScene(composer.getBoundsInLocal()).getMinX() >= 550,
                        "Composer must be below messages, clear of the conversation rail");
            } finally { stage.close(); }
        });
    }
}
