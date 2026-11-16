package com.chatsystem.shared.model;

import com.chatsystem.shared.model.status.*;
import javafx.beans.property.*;
import java.time.Instant;
import java.util.Objects;

/** UI domain state. Domain mutations are called by services, never controllers. */
public final class Conversation {
    private final long id;
    private final ConversationType type;
    private final long createdBy;
    private final Instant createdAt;
    private final ReadOnlyStringWrapper name = new ReadOnlyStringWrapper(this, "name");
    private final ReadOnlyStringWrapper description = new ReadOnlyStringWrapper(this, "description");
    private final ReadOnlyStringWrapper lastMessagePreview = new ReadOnlyStringWrapper(this, "lastMessagePreview");
    private final ReadOnlyObjectWrapper<Instant> lastMessageAt = new ReadOnlyObjectWrapper<>(this, "lastMessageAt");

    public Conversation(long id, ConversationType type, long createdBy, Instant createdAt, String name, String description) {
        this.id = id;
        this.type = Objects.requireNonNull(type);
        this.createdBy = createdBy;
        this.createdAt = Objects.requireNonNull(createdAt);
        updateDetails(name, description);
        lastMessagePreview.set("");
    }

    public long getId() { return id; }

    public ConversationType getType() { return type; }

    public long getCreatedBy() { return createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    public String getName() { return name.get(); }
    public ReadOnlyStringProperty nameProperty() { return name.getReadOnlyProperty(); }

    public String getDescription() { return description.get(); }
    public ReadOnlyStringProperty descriptionProperty() { return description.getReadOnlyProperty(); }

    public String getLastMessagePreview() { return lastMessagePreview.get(); }
    public ReadOnlyStringProperty lastMessagePreviewProperty() { return lastMessagePreview.getReadOnlyProperty(); }

    public Instant getLastMessageAt() { return lastMessageAt.get(); }
    public ReadOnlyObjectProperty<Instant> lastMessageAtProperty() { return lastMessageAt.getReadOnlyProperty(); }

    public void updateDetails(String name, String description) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(description);
        this.name.set(name);
        this.description.set(description);
    }

    public void updatePreview(String preview, Instant sentAt) {
        Objects.requireNonNull(preview);
        Objects.requireNonNull(sentAt);
        this.lastMessageAt.set(sentAt);
        this.lastMessagePreview.set(preview);
    }
}
