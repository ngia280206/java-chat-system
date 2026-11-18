package com.chatsystem.user.controller.social;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.user.*;
import com.chatsystem.user.navigation.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SocialWorkflowControllerTest extends JavaFxTestSupport {
    @Test void searchMessageBlockAndUnblockStayConsistent() throws Exception { workflow((context, stage, navigator) -> {
        navigator.navigate(ViewRequest.of(UserRoute.FRIENDS));
        ListView<?> friends = control(stage, "friendList", ListView.class); assertEquals(3, friends.getItems().size());
        control(stage, "friendSearchField", TextField.class).setText("Mia"); assertEquals(1, friends.getItems().size());
        friends.getSelectionModel().selectFirst(); click(stage, "messageFriendButton"); assertEquals(UserRoute.CHAT_PRIVATE, navigator.currentRequest().route());
        navigator.navigate(ViewRequest.of(UserRoute.FRIENDS)); friends = control(stage, "friendList", ListView.class);
        friends.getSelectionModel().selectFirst(); click(stage, "blockUserButton"); click(stage, "confirmSocialButton"); assertEquals(2, context.social().getFriends().size());
        navigator.navigate(ViewRequest.of(UserRoute.BLOCKED_USERS)); ListView<?> blocked = control(stage, "blockedList", ListView.class);
        assertEquals(2, blocked.getItems().size()); blocked.getSelectionModel().selectFirst(); click(stage, "unblockButton"); click(stage, "confirmUnblockButton");
        assertEquals(1, context.social().getBlockedUsers().size());
    }); }

    @Test void confirmationsCanBeCancelledAndRequestsCanBeResolved() throws Exception { workflow((context, stage, navigator) -> {
        navigator.navigate(ViewRequest.of(UserRoute.FRIENDS)); ListView<?> friends = control(stage, "friendList", ListView.class);
        friends.getSelectionModel().selectFirst(); click(stage, "blockUserButton"); click(stage, "cancelSocialButton"); assertEquals(3, context.social().getFriends().size());
        navigator.navigate(ViewRequest.of(UserRoute.FRIEND_REQUESTS)); ListView<?> outgoing = control(stage, "outgoingList", ListView.class);
        outgoing.getSelectionModel().selectFirst(); click(stage, "cancelRequestButton"); click(stage, "cancelRequestDecisionButton"); assertEquals(1, context.social().getOutgoingRequests().size());
        click(stage, "cancelRequestButton"); click(stage, "confirmRequestButton"); assertTrue(context.social().getOutgoingRequests().isEmpty());
        ListView<?> incoming = control(stage, "incomingList", ListView.class); incoming.getSelectionModel().selectFirst(); click(stage, "declineButton");
        assertTrue(context.social().getIncomingRequests().isEmpty());
    }); }

    @Test void addFriendModalIsReachableAndValidatesWithoutMutation() throws Exception { workflow((context, stage, navigator) -> {
        navigator.navigate(ViewRequest.of(UserRoute.FRIENDS)); int before = context.social().getOutgoingRequests().size(); click(stage, "openAddFriendButton");
        Window dialog = Window.getWindows().stream().filter(window -> window != stage && window.isShowing()).findFirst().orElseThrow();
        assertNotNull(dialog.getScene().getRoot().lookup("#findUserField"));
        ((Button) dialog.getScene().getRoot().lookup("#submitAddFriendButton")).fire(); assertEquals(before, context.social().getOutgoingRequests().size());
        assertFalse(((Label) dialog.getScene().getRoot().lookup("#feedbackLabel")).getText().isBlank()); dialog.hide();
    }); }

    @Test void relationshipRowsRenderTheirDefinedActions() throws Exception { workflow((context, stage, navigator) -> {
        navigator.navigate(ViewRequest.of(UserRoute.FRIENDS)); stage.show(); stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
        assertFalse(stage.getScene().getRoot().lookupAll(".friend-row-action").isEmpty());
        navigator.navigate(ViewRequest.of(UserRoute.FRIEND_REQUESTS)); stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
        assertFalse(stage.getScene().getRoot().lookupAll(".request-row-action").isEmpty());
        navigator.navigate(ViewRequest.of(UserRoute.BLOCKED_USERS)); stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
        assertFalse(stage.getScene().getRoot().lookupAll(".blocked-row-action").isEmpty());
    }); }

    private void workflow(WorkflowAction action) throws Exception { onFx(() -> {
        var context = new UserApplicationContext(); context.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        var stage = new Stage(); try { var navigator = new UserNavigator(stage, context); navigator.start(); action.run(context, stage, navigator); }
        finally { Window.getWindows().stream().filter(window -> window != stage).toList().forEach(Window::hide); stage.close(); }
    }); }
    private interface WorkflowAction { void run(UserApplicationContext context, Stage stage, UserNavigator navigator); }
    private static void click(Stage stage, String id) { control(stage, id, Button.class).fire(); }
    private static <T extends Node> T control(Stage stage, String id, Class<T> type) { Node node = stage.getScene().getRoot().lookup("#" + id); assertNotNull(node, id); return type.cast(node); }
}
