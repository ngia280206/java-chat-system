package com.chatsystem.user.navigation;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.model.status.ProfileVisibility;
import com.chatsystem.user.JavaFxTestSupport;
import com.chatsystem.user.UserApplicationContext;
import com.chatsystem.user.UserMain;
import com.chatsystem.user.ui.FxmlViewLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserNavigatorTest extends JavaFxTestSupport {
    @Test
    void detailRoutesRejectMissingWrongAndNonpositiveTypedIds() {
        for (var route : new UserRoute[]{UserRoute.CHAT_PRIVATE, UserRoute.CHAT_GROUP,
                UserRoute.MESSAGE_SEARCH, UserRoute.GROUP_DETAILS, UserRoute.AI_ASSISTANT, UserRoute.REPORT_DETAIL}) {
            assertThrows(IllegalArgumentException.class, () -> ViewRequest.of(route));
        }
        assertThrows(IllegalArgumentException.class, () -> new ViewRequest.ConversationId(0));
        assertThrows(IllegalArgumentException.class, () -> new ViewRequest.ReportId(-1));
        assertThrows(IllegalArgumentException.class, () -> new ViewRequest(UserRoute.REPORT_DETAIL,
                Optional.of(new ViewRequest.ConversationId(10))));
        assertThrows(IllegalArgumentException.class, () -> new ViewRequest(UserRoute.LOGIN,
                Optional.of(new ViewRequest.ReportId(501))));
        assertEquals(new ViewRequest.ConversationId(10), ViewRequest.conversation(UserRoute.CHAT_PRIVATE, 10).entityId().orElseThrow());
        assertEquals(new ViewRequest.ReportId(501), ViewRequest.report(501).entityId().orElseThrow());
        assertTrue(ViewRequest.of(UserRoute.PROFILE).entityId().isEmpty());
    }

    @Test
    void loginStartsAtExactFixedProfileAndAuthenticationOpensChatHome() throws Exception {
        onFx(() -> {
            var context = new UserApplicationContext();
            var stage = new Stage();
            try {
                var navigator = new UserNavigator(stage, context);
                navigator.start();
                assertEquals(UserRoute.LOGIN, navigator.currentRequest().route());
                assertEquals(1240, stage.getScene().getWidth());
                assertEquals(780, stage.getScene().getHeight());
                assertFalse(stage.isResizable());
                assertThrows(IllegalStateException.class, () -> navigator.navigate(ViewRequest.of(UserRoute.FRIENDS)));
                assertFalse(context.auth().login(MockFixtures.DEMO_EMAIL, "wrong", false).isSuccess());
                assertEquals(UserRoute.LOGIN, navigator.currentRequest().route());
                context.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
                assertEquals(UserRoute.CHAT_HOME, navigator.currentRequest().route());
                assertEquals(1540, stage.getScene().getWidth());
                assertEquals(930, stage.getScene().getHeight());
                assertFalse(stage.isResizable());
                assertTrue(stage.getScene().getStylesheets().get(0).endsWith("/css/dark.css"));
            } finally { stage.close(); }
        });
    }

    @Test
    void sidebarRoutesReuseOneShellAndBindLiveIdentityAndSelectedState() throws Exception {
        onFx(() -> {
            var context = new UserApplicationContext();
            var stage = new Stage();
            try {
                var navigator = new UserNavigator(stage, context);
                navigator.start();
                context.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
                Parent shell = stage.getScene().getRoot();
                assertEquals(1, shell.lookupAll(".sidebar").size());
                String[] ids = {"friendsButton", "groupsButton", "reportsButton", "profileButton", "settingsButton", "chatsButton"};
                UserRoute[] routes = {UserRoute.FRIENDS, UserRoute.GROUP_MANAGEMENT, UserRoute.SPAM_REPORTS,
                        UserRoute.PROFILE, UserRoute.SETTINGS, UserRoute.CHAT_HOME};
                for (int i = 0; i < ids.length; i++) {
                    Button button = (Button) shell.lookup("#" + ids[i]);
                    button.fire();
                    assertEquals(routes[i], navigator.currentRequest().route());
                    assertSame(shell, stage.getScene().getRoot());
                    assertEquals(1, shell.lookupAll(".sidebar").size());
                    assertTrue(button.getStyleClass().contains("nav-active"));
                    assertEquals(1, shell.lookupAll(".nav-active").size());
                    assertFalse(((javafx.scene.layout.StackPane) shell.lookup("#contentHost")).getChildren().isEmpty());
                }
                context.profiles().updateProfile("Taylor Smith", "taylorsmith", "", ProfileVisibility.EVERYONE);
                assertEquals("Taylor Smith", ((Label) shell.lookup("#currentUserName")).getText());
                assertEquals("TS", ((Label) shell.lookup("#currentUserInitials")).getText());
                Button help = (Button) shell.lookup("#helpButton");
                assertTrue(help.isDisabled());
                assertFalse(help.getParent().isDisabled());
                assertEquals("Help and support are unavailable because no support destination is defined.", help.getParent().getAccessibleText());
                context.auth().logout();
                assertEquals(UserRoute.LOGIN, navigator.currentRequest().route());
                assertEquals(1240, stage.getScene().getWidth());
                context.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
                assertEquals(UserRoute.CHAT_HOME, navigator.currentRequest().route());
                assertNotSame(shell, stage.getScene().getRoot());
            } finally { stage.close(); }
        });
    }

    @Test
    void aiProfileIsFixedAndKeepsMainShellAndNavigation() throws Exception {
        onFx(() -> {
            var context = new UserApplicationContext();
            var stage = new Stage();
            var navigator = new UserNavigator(stage, context);
            try {
                navigator.start();
                context.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
                Parent shell = stage.getScene().getRoot();
                navigator.navigate(ViewRequest.conversation(UserRoute.AI_ASSISTANT, 10));
                Stage assistant = navigator.assistantStage().orElseThrow();
                assertEquals(890, assistant.getScene().getWidth());
                assertEquals(890, assistant.getScene().getHeight());
                assertFalse(assistant.isResizable());
                assertSame(stage, assistant.getOwner());
                assertSame(shell, stage.getScene().getRoot());
                assertEquals(UserRoute.CHAT_HOME, navigator.currentRequest().route());
                context.auth().logout();
                assertFalse(assistant.isShowing());
            } finally {
                navigator.assistantStage().ifPresent(Stage::close);
                stage.close();
            }
        });
    }

    @Test
    void authenticationRoutesPreserveTheInProgressResetSession() throws Exception {
        onFx(() -> {
            var context = new UserApplicationContext();
            Stage stage = new Stage();
            try {
                var navigator = new UserNavigator(stage, context);
                navigator.start();
                navigator.navigate(ViewRequest.of(UserRoute.REGISTER));
                navigator.navigate(ViewRequest.of(UserRoute.FORGOT_PASSWORD));
                assertTrue(context.auth().beginPasswordReset(MockFixtures.DEMO_EMAIL).isSuccess());
                navigator.navigate(ViewRequest.of(UserRoute.VERIFICATION_CODE));
                assertTrue(context.auth().verifyResetCode(MockFixtures.RESET_CODE).isSuccess());
                navigator.navigate(ViewRequest.of(UserRoute.RESET_PASSWORD));
                assertEquals(1240, stage.getScene().getWidth());
                assertEquals(780, stage.getScene().getHeight());
                assertFalse(stage.isResizable());
                assertTrue(context.auth().resetPassword("ChangedPass123!", "ChangedPass123!").isSuccess());
                navigator.navigate(ViewRequest.of(UserRoute.LOGIN));
                assertTrue(context.auth().login(MockFixtures.DEMO_EMAIL, "ChangedPass123!", false).isSuccess());
                assertEquals(UserRoute.CHAT_HOME, navigator.currentRequest().route());
            } finally { stage.close(); }
        });
    }

    @Test
    void detailRoutesKeepTypedRequestsAndSelectTheirSidebarSection() throws Exception {
        onFx(() -> {
            var context = new UserApplicationContext();
            Stage stage = new Stage();
            try {
                var navigator = new UserNavigator(stage, context);
                navigator.start();
                context.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
                Parent shell = stage.getScene().getRoot();
                ViewRequest[] requests = {ViewRequest.conversation(UserRoute.CHAT_PRIVATE, 10),
                        ViewRequest.conversation(UserRoute.CHAT_GROUP, 20),
                        ViewRequest.conversation(UserRoute.MESSAGE_SEARCH, 10),
                        ViewRequest.of(UserRoute.FRIEND_REQUESTS), ViewRequest.of(UserRoute.BLOCKED_USERS),
                        ViewRequest.conversation(UserRoute.GROUP_DETAILS, 20),
                        ViewRequest.of(UserRoute.GROUP_INVITATIONS), ViewRequest.report(501),
                        ViewRequest.of(UserRoute.CHANGE_PASSWORD)};
                String[] selected = {"chatsButton", "chatsButton", "chatsButton", "friendsButton",
                        "friendsButton", "groupsButton", "groupsButton", "reportsButton", "settingsButton"};
                for (int i = 0; i < requests.length; i++) {
                    navigator.navigate(requests[i]);
                    assertEquals(requests[i], navigator.currentRequestProperty().get());
                    assertSame(shell, stage.getScene().getRoot());
                    assertEquals(selected[i], shell.lookup(".nav-active").getId());
                    var host = (javafx.scene.layout.StackPane) shell.lookup("#contentHost");
                    assertEquals(requests[i], host.getChildren().get(0).getUserData());
                }
            } finally { stage.close(); }
        });
    }

    @Test
    void missingFxmlAndCssReportTheRequestedClasspathPath() throws Exception {
        onFx(() -> {
            var context = new UserApplicationContext();
            var stage = new Stage();
            try {
                var navigator = new UserNavigator(stage, context);
                var loader = new FxmlViewLoader(context, navigator);
                var fxmlError = assertThrows(IllegalArgumentException.class, () -> loader.load("/fxml/missing.fxml"));
                assertTrue(fxmlError.getMessage().contains("/fxml/missing.fxml"));
                var cssError = assertThrows(IllegalArgumentException.class, () -> loader.resource("/css/missing.css"));
                assertTrue(cssError.getMessage().contains("/css/missing.css"));
                assertThrows(IllegalArgumentException.class, () -> loader.createController(String.class));
            } finally { stage.close(); }
        });
    }

    @Test
    void applicationEntryPointOpensLoginWithClasspathTheme() throws Exception {
        onFx(() -> {
            Stage stage = new Stage();
            try {
                new UserMain().start(stage);
                assertTrue(stage.isShowing());
                assertEquals(1240, stage.getScene().getWidth());
                assertEquals(780, stage.getScene().getHeight());
                assertFalse(stage.isResizable());
                assertTrue(stage.getScene().getStylesheets().get(0).endsWith("/css/dark.css"));
            } finally { stage.close(); }
        });
    }
}
