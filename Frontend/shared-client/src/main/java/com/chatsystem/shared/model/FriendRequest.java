package com.chatsystem.shared.model;

import com.chatsystem.shared.model.status.*;
import javafx.beans.property.*;
import java.time.Instant;
import java.util.Objects;

/** UI domain state. Domain mutations are called by services, never controllers. */
public final class FriendRequest {
    private final long id;
    private final long senderId;
    private final long receiverId;
    private final Instant createdAt;
    private final ReadOnlyObjectWrapper<FriendRequestStatus> status = new ReadOnlyObjectWrapper<>(this, "status");
    private final ReadOnlyObjectWrapper<Instant> respondedAt = new ReadOnlyObjectWrapper<>(this, "respondedAt");

    public FriendRequest(long id, long senderId, long receiverId, Instant createdAt) {
        this.id = id;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.createdAt = Objects.requireNonNull(createdAt);
        status.set(FriendRequestStatus.PENDING);
    }

    public long getId() { return id; }

    public long getSenderId() { return senderId; }

    public long getReceiverId() { return receiverId; }

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
