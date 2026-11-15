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
    private FilteredList<Conversation> filtered;
    private FilterMode filterMode = FilterMode.ALL;
    private enum FilterMode { ALL, UNREAD, GROUPS }

    public ChatHomeController(ChatService chat, UserNavigator navigator) {
        this.chat = Objects.requireNonNull(chat); this.navigator = Objects.requireNonNull(navigator);
    }

    @FXML private void initialize() {
        filtered = new FilteredList<>(chat.getConversations());
        conversationList.setItems(filtered);
        conversationList.setCellFactory(ignored -> new ConversationCell());
        conversationSearchField.textProperty().addListener((o, a, text) -> refreshFilter());
        refreshFilter();
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

    @FXML private void showGroups() { navigator.navigate(ViewRequest.of(UserRoute.GROUP_MANAGEMENT)); }
    @FXML private void newConversation() { navigator.showDialog("/fxml/dialogs/new-conversation.fxml", "New conversation"); }
    @FXML private void filterAll() { filterMode = FilterMode.ALL; refreshFilter(); }
    @FXML private void filterUnread() { filterMode = FilterMode.UNREAD; refreshFilter(); }
    @FXML private void filterGroups() { filterMode = FilterMode.GROUPS; refreshFilter(); }
    private void refreshFilter() {
        String query = conversationSearchField.getText() == null ? "" : conversationSearchField.getText().strip().toLowerCase(Locale.ROOT);
        filtered.setPredicate(c -> (query.isEmpty() || c.getName().toLowerCase(Locale.ROOT).contains(query)
                || c.getLastMessagePreview().toLowerCase(Locale.ROOT).contains(query))
                && (filterMode == FilterMode.ALL || filterMode == FilterMode.UNREAD && chat.isUnread(c.getId())
                || filterMode == FilterMode.GROUPS && c.getType() == ConversationType.GROUP));
    }
}
