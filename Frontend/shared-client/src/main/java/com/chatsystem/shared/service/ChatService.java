package com.chatsystem.shared.service;

import com.chatsystem.shared.model.Conversation;
import com.chatsystem.shared.model.Message;
import com.chatsystem.shared.validation.ValidationResult;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ObservableList;
import java.util.List;

/** Current active account's live, read-only conversations and visible messages. */
public interface ChatService {
    ObservableList<Conversation> getConversations();
    ObservableList<Message> getMessages(long conversationId);
    Conversation getSelectedConversation();
    ReadOnlyObjectProperty<Conversation> selectedConversationProperty();
    int getUnreadCount();
    ReadOnlyIntegerProperty unreadCountProperty();
    boolean isUnread(long conversationId);
    ValidationResult<Void> selectConversation(long conversationId);
    ValidationResult<Conversation> startConversation(long recipientId);
    ValidationResult<Message> sendMessage(long conversationId, String content);
    /** Case-insensitive visible-content search in sequence order; blank matches all visible messages. */
    List<Message> searchMessages(long conversationId, String query);
    ValidationResult<Void> hideMessage(long messageId);
    ValidationResult<Void> clearHistory(long conversationId);
}
