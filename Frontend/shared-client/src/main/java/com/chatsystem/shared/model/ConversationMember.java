package com.chatsystem.shared.model;

import com.chatsystem.shared.model.status.*;
import javafx.beans.property.*;
import java.time.Instant;
import java.util.Objects;

/** UI domain state. Domain mutations are called by services, never controllers. */
public final class ConversationMember {
    private final long conversationId;
    private final long userId;
    private final Instant joinedAt;
    private final ReadOnlyObjectWrapper<GroupRole> role = new ReadOnlyObjectWrapper<>(this, "role");
    private final ReadOnlyObjectWrapper<Instant> leftAt = new ReadOnlyObjectWrapper<>(this, "leftAt");
    private final ReadOnlyIntegerWrapper unreadCount = new ReadOnlyIntegerWrapper(this, "unreadCount");

    public ConversationMember(long conversationId, long userId, GroupRole role, Instant joinedAt) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.joinedAt = Objects.requireNonNull(joinedAt);
        setRole(role);
    }

    public long getConversationId() { return conversationId; }

    public long getUserId() { return userId; }

    public Instant getJoinedAt() { return joinedAt; }

    public GroupRole getRole() { return role.get(); }
    public ReadOnlyObjectProperty<GroupRole> roleProperty() { return role.getReadOnlyProperty(); }

    public Instant getLeftAt() { return leftAt.get(); }
    public ReadOnlyObjectProperty<Instant> leftAtProperty() { return leftAt.getReadOnlyProperty(); }

    public int getUnreadCount() { return unreadCount.get(); }
    public ReadOnlyIntegerProperty unreadCountProperty() { return unreadCount.getReadOnlyProperty(); }

    // Private per-user visibility boundary is not a displayed field.
    private long historyClearedThroughSeq;

    public long getHistoryClearedThroughSeq() { return historyClearedThroughSeq; }

    public void clearHistoryThrough(long sequence) {
        if (sequence < 0) throw new IllegalArgumentException("Sequence cannot be negative");
        historyClearedThroughSeq = Math.max(historyClearedThroughSeq, sequence);
    }

    public void setRole(GroupRole role) { this.role.set(Objects.requireNonNull(role)); }
    public void setLeftAt(Instant leftAt) { this.leftAt.set(leftAt); }

    public void setUnreadCount(int unreadCount) {
        if (unreadCount < 0) throw new IllegalArgumentException("Unread count cannot be negative");
        this.unreadCount.set(unreadCount);
    }
}
