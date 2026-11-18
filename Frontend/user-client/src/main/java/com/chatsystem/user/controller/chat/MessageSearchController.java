package com.chatsystem.user.controller.chat;

import com.chatsystem.shared.model.Message;
import com.chatsystem.shared.service.ChatService;
import com.chatsystem.user.navigation.*;
import com.chatsystem.user.ui.DisabledControlSupport;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import java.util.Objects;
import java.util.Comparator;

public final class MessageSearchController {

    private final ChatService chat;
    private final UserNavigator navigator;
    private long conversationId;
    @FXML
    private TextField queryField;
    @FXML
    private ListView<Message> resultList;
    @FXML
    private ComboBox<com.chatsystem.shared.model.Conversation> conversationScope;
    @FXML
    private Button imagesButton, linksButton, filesButton;
    @FXML
    private StackPane imagesWrapper, linksWrapper, filesWrapper;

    public MessageSearchController(ChatService chat, UserNavigator navigator) {
        this.chat = Objects.requireNonNull(chat);
        this.navigator = Objects.requireNonNull(navigator);
    }

    @FXML
    private void initialize() {
        resultList.setCellFactory(ignored -> new com.chatsystem.user.ui.cell.MessageBubble());
        DisabledControlSupport.install(imagesButton, imagesWrapper, "Image search is unavailable because attachments are not supported in this demo.");
        DisabledControlSupport.install(linksButton, linksWrapper, "Link-specific filtering is unavailable; use message text search.");
        DisabledControlSupport.install(filesButton, filesWrapper, "File search is unavailable because attachments are not supported in this demo.");
        queryField.setOnAction(event -> search());
        conversationScope.setItems(chat.getConversations());
        conversationScope.setConverter(new javafx.util.StringConverter<>() {
            public String toString(com.chatsystem.shared.model.Conversation c) {
                return c == null ? "All conversations" : (c.getName().isBlank() ? "Direct conversation" : c.getName());
            }

            public com.chatsystem.shared.model.Conversation fromString(String text) {
                return null;
            }
        });
        resultList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openResult();
        
            }});
    }

    public void present(long id) {
        conversationId = id;
        conversationScope.getItems().stream().filter(c -> c.getId() == id).findFirst()
                .ifPresent(c -> conversationScope.getSelectionModel().select(c));
        search();
    }

    @FXML
    private void allConversations() {
        conversationScope.getSelectionModel().clearSelection();
        search();
    }

    @FXML
    private void search() {
        var scope = conversationScope.getSelectionModel().getSelectedItem();
        var results = scope == null ? chat.getConversations().stream().flatMap(c -> chat.searchMessages(c.getId(), queryField.getText()).stream()).sorted(Comparator.comparing(Message::getSentAt).reversed()).toList()
                : chat.searchMessages(scope.getId(), queryField.getText());
        resultList.setItems(FXCollections.observableArrayList(results));
    }

    @FXML
    private void openResult() {
        Message message = resultList.getSelectionModel().getSelectedItem();
        if (message == null) {
            return;
        }
        var conversation = chat.getConversations().stream().filter(c -> c.getId() == message.getConversationId()).findFirst().orElse(null);
        if (conversation == null) {
            return;
        }
        navigator.navigate(ViewRequest.conversation(conversation.getType() == com.chatsystem.shared.model.status.ConversationType.GROUP ? UserRoute.CHAT_GROUP : UserRoute.CHAT_PRIVATE, conversation.getId()));
    }

    @FXML
    private void back() {
        var conversation = chat.getConversations().stream().filter(c -> c.getId() == conversationId).findFirst().orElse(null);
        if (conversation == null) {
            navigator.navigate(ViewRequest.of(UserRoute.CHAT_HOME));
            return;
        }
        navigator.navigate(ViewRequest.conversation(conversation.getType() == com.chatsystem.shared.model.status.ConversationType.GROUP ? UserRoute.CHAT_GROUP : UserRoute.CHAT_PRIVATE, conversationId));
    }
}
