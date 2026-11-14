package com.chatsystem.shared.model;

import java.time.Instant;
import java.util.Objects;

/** Immutable text message. Hiding/clearing history belongs to the viewing user's state. */
public final class Message {
    private final long id;
    private final long conversationId;
    private final long senderId;
    private final long sequence;
    private final String content;
    private final Instant sentAt;

    public Message(long id, long conversationId, long senderId, long sequence, String content, Instant sentAt) {
        if (sequence <= 0) throw new IllegalArgumentException("Message sequence must be positive");
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.sequence = sequence;
        this.content = Objects.requireNonNull(content);
        this.sentAt = Objects.requireNonNull(sentAt);
    }

    public long getId() { return id; }

    public long getConversationId() { return conversationId; }

    public long getSenderId() { return senderId; }

    public long getSequence() { return sequence; }

    public String getContent() { return content; }

    public Instant getSentAt() { return sentAt; }
}
