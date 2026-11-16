package com.chatsystem.user.controller.chat;

import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.user.JavaFxTestSupport;
import com.chatsystem.user.UserApplicationContext;
import com.chatsystem.user.navigation.UserNavigator;
import com.chatsystem.user.navigation.UserRoute;
import com.chatsystem.user.navigation.ViewRequest;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatWorkflowControllerTest extends JavaFxTestSupport {
    private record Workflow(UserApplicationContext context, Stage stage, UserNavigator navigator) implements AutoCloseable {
        static Workflow open() {
            var context = new UserApplicationContext();
            assertTrue(context.auth().login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false).isSuccess());
            var stage = new Stage();
            var navigator = new UserNavigator(stage, context);
            navigator.start();
            return new Workflow(context, stage, navigator);
        }
        <T extends Node> T control(String id, Class<T> type) {
            Node node = stage.getScene().getRoot().lookup("#" + id);
            assertNotNull(node, "Missing chat control: " + id);
            return type.cast(node);
        }
        void click(String id) { control(id, Button.class).fire(); }
        @Override public void close() { navigator.assistantStage().ifPresent(Stage::close); stage.close(); }
    }

    @Test void selectingSendingSearchingHidingAndClearingMutateVisibleState() throws Exception {
        onFx(() -> { try (var w = Workflow.open()) {
            assertEquals(UserRoute.CHAT_HOME, w.navigator.currentRequest().route());
            assertFalse(w.control("conversationList", ListView.class).getItems().isEmpty());
            w.navigator.navigate(ViewRequest.conversation(UserRoute.CHAT_PRIVATE, MockFixtures.DIRECT_CONVERSATION_ID));
            int before = w.context.chat().getMessages(MockFixtures.DIRECT_CONVERSATION_ID).size();
            w.control("messageField", TextField.class).setText("A new visible message");
            w.click("sendButton");
            assertEquals(before + 1, w.context.chat().getMessages(MockFixtures.DIRECT_CONVERSATION_ID).size());
            assertEquals("", w.control("messageField", TextField.class).getText());

            w.click("searchButton");
            assertEquals(UserRoute.MESSAGE_SEARCH, w.navigator.currentRequest().route());
            w.control("queryField", TextField.class).setText("new visible");
            w.click("submitSearchButton");
            assertEquals(1, w.control("resultList", ListView.class).getItems().size());

            w.navigator.navigate(ViewRequest.conversation(UserRoute.CHAT_PRIVATE, MockFixtures.DIRECT_CONVERSATION_ID));
            @SuppressWarnings("unchecked") ListView<com.chatsystem.shared.model.Message> messages = w.control("messageList", ListView.class);
            messages.getSelectionModel().selectFirst();
            w.click("hideMessageButton");
            w.click("confirmActionButton");
            assertEquals(before, w.context.chat().getMessages(MockFixtures.DIRECT_CONVERSATION_ID).size());
            w.click("clearHistoryButton");
            w.click("confirmActionButton");
            assertTrue(w.context.chat().getMessages(MockFixtures.DIRECT_CONVERSATION_ID).isEmpty());
            assertTrue(w.context.chat().sendMessage(MockFixtures.DIRECT_CONVERSATION_ID, "After clear").isSuccess());
            assertEquals(1, w.context.chat().getMessages(MockFixtures.DIRECT_CONVERSATION_ID).size());
        }});
    }

    @Test void invalidComposerAndNewConversationDoNotMutateState() throws Exception {
        onFx(() -> { try (var w = Workflow.open()) {
            w.navigator.navigate(ViewRequest.conversation(UserRoute.CHAT_PRIVATE, MockFixtures.DIRECT_CONVERSATION_ID));
            int messages = w.context.chat().getMessages(MockFixtures.DIRECT_CONVERSATION_ID).size();
            w.click("sendButton");
            assertEquals(messages, w.context.chat().getMessages(MockFixtures.DIRECT_CONVERSATION_ID).size());
            assertTrue(w.control("feedbackLabel", Label.class).getText().contains("required"));
            assertTrue(w.control("attachmentButton", Button.class).isDisabled());
            assertEquals("Attachments are unavailable in the frontend-only demo.",
                    w.control("attachmentWrapper", javafx.scene.layout.StackPane.class).getAccessibleText());
            assertTrue(w.control("detailsButton", Button.class).isDisabled());
            assertFalse(w.control("detailsWrapper", javafx.scene.layout.StackPane.class).getAccessibleText().isBlank());

            int conversations = w.context.chat().getConversations().size();
            w.navigator.navigate(ViewRequest.of(UserRoute.CHAT_HOME));
            w.click("newConversationButton");
            Window dialog = Window.getWindows().stream().filter(window -> window != w.stage && window.isShowing()).findFirst().orElseThrow();
            ((Button) dialog.getScene().getRoot().lookup("#submitNewConversationButton")).fire();
            assertEquals(conversations, w.context.chat().getConversations().size());
            assertFalse(((Label) dialog.getScene().getRoot().lookup("#feedbackLabel")).getText().isBlank());
            ((Button) dialog.getScene().getRoot().lookup("#cancelNewConversationButton")).fire();
        }});
    }

    @Test void assistantUsesSelectedConversationAndRejectsBlankPrompt() throws Exception {
        onFx(() -> { try (var w = Workflow.open()) {
            w.navigator.navigate(ViewRequest.conversation(UserRoute.CHAT_GROUP, MockFixtures.GROUP_CONVERSATION_ID));
            @SuppressWarnings("unchecked") ListView<com.chatsystem.shared.model.Message> messages = w.control("messageList", ListView.class);
            messages.getSelectionModel().selectFirst(); String selectedText = messages.getSelectionModel().getSelectedItem().getContent();
            w.click("assistantButton");
            Stage assistant = w.navigator.assistantStage().orElseThrow();
            TextArea prompt = (TextArea) assistant.getScene().getRoot().lookup("#promptField");
            Button send = (Button) assistant.getScene().getRoot().lookup("#sendPromptButton");
            Label response = (Label) assistant.getScene().getRoot().lookup("#responseLabel");
            assertTrue(prompt.getText().contains(selectedText));
            ((Button) assistant.getScene().getRoot().lookup("#summarizePromptButton")).fire();
            assertTrue(prompt.getText().toLowerCase().contains("summarize"));
            prompt.clear(); send.fire(); assertTrue(response.getText().contains("Enter a prompt"));
            prompt.setText("Please summarize"); send.fire();
            assertTrue(response.getText().startsWith("Recent conversation:"));
            assertEquals(3, w.context.chat().getMessages(MockFixtures.GROUP_CONVERSATION_ID).size());
        }});
    }

    @Test void destructiveActionsCanBeCancelledWithoutMutation() throws Exception {
        onFx(() -> { try (var w = Workflow.open()) {
            w.navigator.navigate(ViewRequest.conversation(UserRoute.CHAT_PRIVATE, MockFixtures.DIRECT_CONVERSATION_ID));
            int before = w.context.chat().getMessages(MockFixtures.DIRECT_CONVERSATION_ID).size();
            @SuppressWarnings("unchecked") ListView<com.chatsystem.shared.model.Message> messages = w.control("messageList", ListView.class);
            messages.getSelectionModel().selectFirst();
            w.click("hideMessageButton"); w.click("cancelActionButton");
            w.click("clearHistoryButton"); w.click("cancelActionButton");
            assertEquals(before, w.context.chat().getMessages(MockFixtures.DIRECT_CONVERSATION_ID).size());
        }});
    }

    @Test void messagesRenderAccountNamesAndAlignCurrentUsersMessagesRight() throws Exception {
        onFx(() -> { try (var w = Workflow.open()) {
            w.navigator.navigate(ViewRequest.conversation(UserRoute.CHAT_GROUP, MockFixtures.GROUP_CONVERSATION_ID));
            w.stage.show(); w.stage.getScene().getRoot().applyCss(); w.stage.getScene().getRoot().layout();
            assertFalse(w.stage.getScene().getRoot().lookupAll(".message-mine").isEmpty());
            assertFalse(w.stage.getScene().getRoot().lookupAll(".message-theirs").isEmpty());
            var names=w.stage.getScene().getRoot().lookupAll(".message-sender").stream().map(node -> ((Label)node).getText()).toList();
            assertTrue(names.stream().anyMatch(name -> name.equals("You")));
            assertTrue(names.stream().anyMatch(name -> !name.startsWith("Member #") && !name.equals("You")));
        }});
    }

    @Test void filtersSearchScopeResultNavigationAndNewConversationModalAreReachable() throws Exception {
        onFx(() -> { try (var w = Workflow.open()) {
            w.click("groupsFilterButton");
            @SuppressWarnings("unchecked") ListView<com.chatsystem.shared.model.Conversation> conversations = w.control("conversationList", ListView.class);
            assertTrue(conversations.getItems().stream().allMatch(c -> c.getType() == com.chatsystem.shared.model.status.ConversationType.GROUP));
            w.click("allFilterButton"); assertTrue(conversations.getItems().size() > 1);
            w.click("newConversationButton");
            Window dialog = Window.getWindows().stream().filter(window -> window != w.stage && window.isShowing()).findFirst().orElseThrow();
            assertNotNull(dialog.getScene().getRoot().lookup("#recipientChoice")); dialog.hide();
            w.navigator.navigate(ViewRequest.conversation(UserRoute.MESSAGE_SEARCH, MockFixtures.DIRECT_CONVERSATION_ID));
            @SuppressWarnings("unchecked") ComboBox<com.chatsystem.shared.model.Conversation> scope = w.control("conversationScope", ComboBox.class);
            scope.getSelectionModel().clearSelection();
            w.control("queryField", TextField.class).setText("palette"); w.click("submitSearchButton");
            @SuppressWarnings("unchecked") ListView<com.chatsystem.shared.model.Message> results = w.control("resultList", ListView.class);
            assertFalse(results.getItems().isEmpty()); results.getSelectionModel().selectFirst(); w.click("openResultButton");
            assertEquals(UserRoute.CHAT_GROUP, w.navigator.currentRequest().route());
        }});
    }

    @Test void messageSearchHonorsIncomingScopeAndCanReturnToAllConversations() throws Exception {
        onFx(() -> { try (var w = Workflow.open()) {
            w.navigator.navigate(ViewRequest.conversation(UserRoute.MESSAGE_SEARCH, MockFixtures.DIRECT_CONVERSATION_ID));
            @SuppressWarnings("unchecked") ComboBox<com.chatsystem.shared.model.Conversation> scope = w.control("conversationScope", ComboBox.class);
            assertNotNull(scope.getValue());
            assertEquals(MockFixtures.DIRECT_CONVERSATION_ID, scope.getValue().getId());
            w.control("queryField", TextField.class).setText("palette"); w.click("submitSearchButton");
            assertTrue(w.control("resultList", ListView.class).getItems().isEmpty());
            w.click("allConversationsButton");
            w.click("submitSearchButton");
            assertFalse(w.control("resultList", ListView.class).getItems().isEmpty());
        }});
    }
}
