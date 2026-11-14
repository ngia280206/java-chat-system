package com.chatsystem.shared.model;

import com.chatsystem.shared.model.status.*;
import javafx.beans.property.*;
import java.time.Instant;
import java.util.Objects;

/** UI domain state. Domain mutations are called by services, never controllers. */
public final class GroupInvitation {
    private final long id;
    private final long conversationId;
    private final long inviterId;
    private final long inviteeId;
    private final Instant createdAt;
    private final ReadOnlyObjectWrapper<FriendRequestStatus> status = new ReadOnlyObjectWrapper<>(this, "status");
    private final ReadOnlyObjectWrapper<Instant> respondedAt = new ReadOnlyObjectWrapper<>(this, "respondedAt");

    public GroupInvitation(long id, long conversationId, long inviterId, long inviteeId, Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
        this.createdAt = Objects.requireNonNull(createdAt);
        status.set(FriendRequestStatus.PENDING);
    }

    public long getId() { return id; }

    public long getConversationId() { return conversationId; }

    public long getInviterId() { return inviterId; }

    public long getInviteeId() { return inviteeId; }

    public Instant getCreatedAt() { return createdAt; }

    public FriendRequestStatus getStatus() { return status.get(); }
    public ReadOnlyObjectProperty<FriendRequestStatus> statusProperty() { return status.getReadOnlyProperty(); }

    public Instant getRespondedAt() { return respondedAt.get(); }
    public ReadOnlyObjectProperty<Instant> respondedAtProperty() { return respondedAt.getReadOnlyProperty(); }

    /** Invitations use the same pending/accepted/rejected/cancelled decision vocabulary as requests. */
    public void respond(FriendRequestStatus status, Instant respondedAt) {
        Objects.requireNonNull(status);
        Objects.requireNonNull(respondedAt);
        if (status == FriendRequestStatus.PENDING) throw new IllegalArgumentException("A response must resolve the request");
        this.respondedAt.set(respondedAt);
        this.status.set(status);
    }
}
