package com.chatsystem.user.controller.chat;
import com.chatsystem.shared.service.ChatService;
import com.chatsystem.user.navigation.*;
import javafx.fxml.FXML;
public final class ChatGroupController extends ChatConversationController {
    public ChatGroupController(ChatService chat, UserNavigator navigator) { super(chat, navigator); }
    @FXML private void groupDetails() { navigator.navigate(ViewRequest.conversation(UserRoute.GROUP_DETAILS, conversationId())); }
}
