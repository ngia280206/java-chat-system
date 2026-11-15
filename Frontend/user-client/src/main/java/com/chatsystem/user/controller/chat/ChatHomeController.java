package com.chatsystem.user.controller.chat;

import com.chatsystem.shared.model.Conversation;
import com.chatsystem.shared.model.status.ConversationType;
import com.chatsystem.shared.service.ChatService;
import com.chatsystem.user.navigation.UserNavigator;
import com.chatsystem.user.navigation.UserRoute;
import com.chatsystem.user.navigation.ViewRequest;
import com.chatsystem.user.ui.cell.ConversationCell;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Locale;
import java.util.Objects;

public final class ChatHomeController {
    private final ChatService chat;
    private final UserNavigator navigator;
    @FXML private TextField conversationSearchField;
    @FXML private ListView<Conversation> conversationList;
    @FXML private TextField recipientField;
    @FXML private Label conversationFeedbackLabel;

    public ChatHomeController(ChatService chat, UserNavigator navigator) {
        this.chat = Objects.requireNonNull(chat); this.navigator = Objects.requireNonNull(navigator);
    }

    @FXML private void initialize() {
        var filtered = new FilteredList<>(chat.getConversations());
        conversationList.setItems(filtered);
        conversationList.setCellFactory(ignored -> new ConversationCell());
        conversationSearchField.textProperty().addListener((o, a, text) -> filtered.setPredicate(c -> {
            String query = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
            return query.isEmpty() || c.getName().toLowerCase(Locale.ROOT).contains(query)
                    || c.getLastMessagePreview().toLowerCase(Locale.ROOT).contains(query);
        }));
        conversationList.setOnMouseClicked(event -> openSelectedConversation());
        conversationList.setOnKeyPressed(event -> { if (event.getCode() == javafx.scene.input.KeyCode.ENTER) openSelectedConversation(); });
    }

    @FXML private void openSelectedConversation() {
        Conversation selected = conversationList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        chat.selectConversation(selected.getId());
        navigator.navigate(ViewRequest.conversation(selected.getType() == ConversationType.GROUP
                ? UserRoute.CHAT_GROUP : UserRoute.CHAT_PRIVATE, selected.getId()));
    }

    @FXML private void startConversation() {
        long recipient;
        try { recipient = Long.parseLong(recipientField.getText().strip()); }
        catch (RuntimeException exception) { show("Enter a valid recipient ID"); return; }
        var result = chat.startConversation(recipient);
        if (!result.isSuccess()) { show(result.errors().get(0).message()); return; }
        Conversation conversation = result.value().orElseThrow();
        chat.selectConversation(conversation.getId());
        navigator.navigate(ViewRequest.conversation(UserRoute.CHAT_PRIVATE, conversation.getId()));
    }

    @FXML private void showGroups() { navigator.navigate(ViewRequest.of(UserRoute.GROUP_MANAGEMENT)); }
    private void show(String text) { conversationFeedbackLabel.setText(text); }
}
