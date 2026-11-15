package com.chatsystem.user.controller.chat;
import com.chatsystem.shared.service.ChatService;
import com.chatsystem.user.navigation.UserNavigator;
public final class ChatPrivateController extends ChatConversationController {
    public ChatPrivateController(ChatService chat, UserNavigator navigator) { super(chat, navigator); }
}
