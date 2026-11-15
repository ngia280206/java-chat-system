package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.model.Conversation;
import com.chatsystem.shared.model.Message;
import com.chatsystem.shared.service.*;
import com.chatsystem.shared.validation.*;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class PredefinedAssistantService implements AssistantService {
    private final ChatService chat;

    public PredefinedAssistantService(ChatService chat) { this.chat = Objects.requireNonNull(chat); }

    @Override public ValidationResult<String> reply(long conversationId, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return ValidationResult.failure(List.of(ValidationError.field("prompt", "Enter a prompt")));
        }
        Conversation conversation = chat.getConversations().stream().filter(c -> c.getId() == conversationId).findFirst().orElse(null);
        if (conversation == null) return ValidationResult.failure(List.of(ValidationError.global("Choose a visible conversation")));
        String text = MockServiceQueries.normalize(prompt);
        if (text.contains("summar")) {
            List<Message> messages = chat.getMessages(conversationId);
            if (messages.isEmpty()) return ValidationResult.success("There are no visible messages to summarize yet.");
            return ValidationResult.success("Recent conversation: " + messages.stream().skip(Math.max(0, messages.size() - 3))
                    .map(Message::getContent).collect(Collectors.joining(" | ")));
        }
        if (text.contains("search") || text.contains("help")) {
            return ValidationResult.success("Use conversation search to find visible messages. You can also ask me to summarize the recent discussion.");
        }
        String context = conversation.getName().isBlank() ? "this direct conversation" : conversation.getName();
        return ValidationResult.success("For " + context + ", try agreeing on one next step and who will take it. Ask for a summary to review the recent discussion.");
    }
}
