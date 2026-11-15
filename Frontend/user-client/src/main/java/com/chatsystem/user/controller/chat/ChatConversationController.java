package com.chatsystem.user.controller.chat;

import com.chatsystem.shared.model.*;
import com.chatsystem.shared.service.ChatService;
import com.chatsystem.user.navigation.*;
import com.chatsystem.user.ui.DisabledControlSupport;
import com.chatsystem.user.ui.cell.MessageBubble;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

import java.util.Objects;

abstract class ChatConversationController {
    protected final ChatService chat;
    protected final UserNavigator navigator;
    private long conversationId;
    @FXML private Label conversationTitle;
    @FXML private ListView<Message> messageList;
    @FXML private TextField messageField;
    @FXML private Label feedbackLabel;
    @FXML private Button attachmentButton;
    @FXML private StackPane attachmentWrapper;

    ChatConversationController(ChatService chat, UserNavigator navigator) {
        this.chat = Objects.requireNonNull(chat); this.navigator = Objects.requireNonNull(navigator);
    }

    @FXML private void initialize() {
        messageList.setCellFactory(ignored -> new MessageBubble());
        DisabledControlSupport.install(attachmentButton, attachmentWrapper,
                "Attachments are unavailable in the frontend-only demo.");
        messageField.setOnAction(event -> send());
    }

    public void present(long id) {
        conversationId = id;
        var selected = chat.selectConversation(id);
        if (!selected.isSuccess()) { feedback(selected.errors().get(0).message()); return; }
        Conversation conversation = chat.getSelectedConversation();
        conversationTitle.setText(conversation.getName().isBlank() ? "Direct conversation" : conversation.getName());
        messageList.setItems(chat.getMessages(id));
    }

    @FXML private void send() {
        var result = chat.sendMessage(conversationId, messageField.getText());
        if (!result.isSuccess()) { feedback(result.errors().get(0).message()); return; }
        messageField.clear(); feedback(""); messageList.scrollTo(result.value().orElseThrow());
    }

    @FXML private void hideSelected() {
        Message message = messageList.getSelectionModel().getSelectedItem();
        if (message == null) { feedback("Choose a message to hide"); return; }
        var result = chat.hideMessage(message.getId());
        feedback(result.isSuccess() ? "Message hidden for you" : result.errors().get(0).message());
    }

    @FXML private void clearHistory() {
        var result = chat.clearHistory(conversationId);
        feedback(result.isSuccess() ? "Conversation history cleared for you" : result.errors().get(0).message());
    }

    @FXML private void search() { navigator.navigate(ViewRequest.conversation(UserRoute.MESSAGE_SEARCH, conversationId)); }
    @FXML private void assistant() { navigator.navigate(ViewRequest.conversation(UserRoute.AI_ASSISTANT, conversationId)); }
    @FXML private void back() { navigator.navigate(ViewRequest.of(UserRoute.CHAT_HOME)); }
    protected long conversationId() { return conversationId; }
    protected void feedback(String text) { feedbackLabel.setText(text); }
}
