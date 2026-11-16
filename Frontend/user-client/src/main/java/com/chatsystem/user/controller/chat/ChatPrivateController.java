package com.chatsystem.user.controller.chat;

import com.chatsystem.shared.service.AdminService;
import com.chatsystem.shared.service.ChatService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.user.navigation.UserNavigator;

public final class ChatPrivateController extends ChatConversationController {
    public ChatPrivateController(ChatService chat, UserSession session, AdminService directory, UserNavigator navigator) {
        super(chat, session, directory, navigator);
    }
}
