package com.chatsystem.shared.service;

import com.chatsystem.shared.validation.ValidationResult;

/** Local simulated assistant. Replies do not send messages or contact external services. */
public interface AssistantService {
    ValidationResult<String> reply(long conversationId, String prompt);
}
