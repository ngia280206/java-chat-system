package com.chatsystem.shared.model;

import com.chatsystem.shared.model.status.ActivityType;
import java.time.Instant;
import java.util.Objects;

public record ActivityEvent(long id, long userId, ActivityType type, Long conversationId, Instant occurredAt) {
    public ActivityEvent {
        Objects.requireNonNull(type);
        Objects.requireNonNull(occurredAt);
        if (type == ActivityType.MESSAGE_SENT && conversationId == null) {
            throw new IllegalArgumentException("A message activity needs a conversation");
        }
    }
}
