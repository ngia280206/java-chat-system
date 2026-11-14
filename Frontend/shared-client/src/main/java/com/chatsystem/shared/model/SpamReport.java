package com.chatsystem.shared.model;

import com.chatsystem.shared.model.status.*;
import javafx.beans.property.*;
import java.time.Instant;
import java.util.Objects;

/** UI domain state. Domain mutations are called by services, never controllers. */
public final class SpamReport {
    private final long id;
    private final long reporterId;
    private final long reportedUserId;
    private final long messageId;
    private final String reason;
    private final Instant createdAt;
    private final ReadOnlyObjectWrapper<ReportStatus> status = new ReadOnlyObjectWrapper<>(this, "status");
    private final ReadOnlyStringWrapper reviewNotes = new ReadOnlyStringWrapper(this, "reviewNotes");

    public SpamReport(long id, long reporterId, long reportedUserId, long messageId, String reason, Instant createdAt) {
        this.id = id;
        this.reporterId = reporterId;
        this.reportedUserId = reportedUserId;
        this.messageId = messageId;
        this.reason = Objects.requireNonNull(reason);
        this.createdAt = Objects.requireNonNull(createdAt);
        status.set(ReportStatus.PENDING);
        reviewNotes.set("");
    }

    public long getId() { return id; }

    public long getReporterId() { return reporterId; }

    public long getReportedUserId() { return reportedUserId; }

    public long getMessageId() { return messageId; }

    public String getReason() { return reason; }

    public Instant getCreatedAt() { return createdAt; }

    public ReportStatus getStatus() { return status.get(); }
    public ReadOnlyObjectProperty<ReportStatus> statusProperty() { return status.getReadOnlyProperty(); }

    public String getReviewNotes() { return reviewNotes.get(); }
    public ReadOnlyStringProperty reviewNotesProperty() { return reviewNotes.getReadOnlyProperty(); }

    /** Records a moderation decision; account sanctions are a separate service operation. */
    public void review(ReportStatus status, String notes) {
        Objects.requireNonNull(status);
        Objects.requireNonNull(notes);
        if (status == ReportStatus.PENDING) throw new IllegalArgumentException("Review must resolve the report");
        reviewNotes.set(notes);
        this.status.set(status);
    }
}
