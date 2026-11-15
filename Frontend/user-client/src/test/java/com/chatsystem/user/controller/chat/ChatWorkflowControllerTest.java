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
            assertEquals(before, w.context.chat().getMessages(MockFixtures.DIRECT_CONVERSATION_ID).size());
            w.click("clearHistoryButton");
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

            int conversations = w.context.chat().getConversations().size();
            w.navigator.navigate(ViewRequest.of(UserRoute.CHAT_HOME));
            w.control("recipientField", TextField.class).setText("99999");
            w.click("startConversationButton");
            assertEquals(conversations, w.context.chat().getConversations().size());
            assertFalse(w.control("conversationFeedbackLabel", Label.class).getText().isBlank());
        }});
    }

    @Test void assistantUsesSelectedConversationAndRejectsBlankPrompt() throws Exception {
        onFx(() -> { try (var w = Workflow.open()) {
            w.navigator.navigate(ViewRequest.conversation(UserRoute.CHAT_GROUP, MockFixtures.GROUP_CONVERSATION_ID));
            w.click("assistantButton");
            Stage assistant = w.navigator.assistantStage().orElseThrow();
            TextArea prompt = (TextArea) assistant.getScene().getRoot().lookup("#promptField");
            Button send = (Button) assistant.getScene().getRoot().lookup("#sendPromptButton");
            Label response = (Label) assistant.getScene().getRoot().lookup("#responseLabel");
            send.fire();
            assertTrue(response.getText().contains("Enter a prompt"));
            prompt.setText("Please summarize"); send.fire();
            assertTrue(response.getText().startsWith("Recent conversation:"));
            assertEquals(3, w.context.chat().getMessages(MockFixtures.GROUP_CONVERSATION_ID).size());
        }});
    }
}
