package com.chatsystem.user.controller.chat;

import com.chatsystem.shared.service.AdminService;
import com.chatsystem.shared.service.ChatService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.user.navigation.*;
import javafx.fxml.FXML;

public final class ChatGroupController extends ChatConversationController {
    private final AdminService directory;
    @FXML private javafx.scene.control.ListView<com.chatsystem.shared.model.ConversationMember> chatMemberList;
    public ChatGroupController(ChatService chat, UserSession session, AdminService directory, UserNavigator navigator) {
        super(chat, session, directory, navigator);
        this.directory=directory;
    }
    @Override public void present(long id) {
        super.present(id);
        chatMemberList.setItems(javafx.collections.FXCollections.observableArrayList(directory.getGroupMembers(id)));
        chatMemberList.setCellFactory(ignored -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(com.chatsystem.shared.model.ConversationMember member,boolean empty) {
                super.updateItem(member,empty);setText(null);
                if(empty||member==null){setGraphic(null);return;}
                var user=directory.getUser(member.getUserId()).orElse(null);
                var name=new javafx.scene.control.Label(user==null?"Unknown user":user.getDisplayName());name.getStyleClass().add("list-title");
                var role=new javafx.scene.control.Label(member.getRole().name());role.getStyleClass().add("tiny-muted");
                setGraphic(new javafx.scene.layout.VBox(5,name,role));
            }
        });
    }
    @FXML private void groupDetails() {
        navigator.navigate(ViewRequest.conversation(UserRoute.GROUP_DETAILS, conversationId()));
    }
}
