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

public final class MessageSearchController {
    private final ChatService chat; private final UserNavigator navigator; private long conversationId;
    @FXML private TextField queryField; @FXML private ListView<Message> resultList;
    @FXML private Button imagesButton, linksButton, filesButton;
    @FXML private StackPane imagesWrapper, linksWrapper, filesWrapper;
    public MessageSearchController(ChatService chat, UserNavigator navigator) { this.chat=Objects.requireNonNull(chat); this.navigator=Objects.requireNonNull(navigator); }
    @FXML private void initialize() {
        resultList.setCellFactory(ignored -> new com.chatsystem.user.ui.cell.MessageBubble());
        DisabledControlSupport.install(imagesButton, imagesWrapper, "Image search is unavailable because attachments are not supported in this demo.");
        DisabledControlSupport.install(linksButton, linksWrapper, "Link-specific filtering is unavailable; use message text search.");
        DisabledControlSupport.install(filesButton, filesWrapper, "File search is unavailable because attachments are not supported in this demo.");
        queryField.setOnAction(event -> search());
    }
    public void present(long id) { conversationId=id; search(); }
    @FXML private void search() { resultList.setItems(FXCollections.observableArrayList(chat.searchMessages(conversationId, queryField.getText()))); }
    @FXML private void back() {
        var conversation=chat.getConversations().stream().filter(c->c.getId()==conversationId).findFirst().orElse(null);
        if(conversation==null){ navigator.navigate(ViewRequest.of(UserRoute.CHAT_HOME)); return; }
        navigator.navigate(ViewRequest.conversation(conversation.getType()==com.chatsystem.shared.model.status.ConversationType.GROUP?UserRoute.CHAT_GROUP:UserRoute.CHAT_PRIVATE, conversationId));
    }
}
