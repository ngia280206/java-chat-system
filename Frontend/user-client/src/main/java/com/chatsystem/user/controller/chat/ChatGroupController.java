package com.chatsystem.user.controller.chat;

import com.chatsystem.shared.service.AdminService;
import com.chatsystem.shared.service.ChatService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.user.navigation.*;
import javafx.fxml.FXML;

public final class ChatGroupController extends ChatConversationController {
    public ChatGroupController(ChatService chat, UserSession session, AdminService directory, UserNavigator navigator) {
        super(chat, session, directory, navigator);
    }
    @FXML private void groupDetails() {
        navigator.navigate(ViewRequest.conversation(UserRoute.GROUP_DETAILS, conversationId()));
    }
}
