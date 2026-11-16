package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.Message;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.LongFunction;

public final class MessageBubble extends ListCell<Message> {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private final long currentUserId;
    private final LongFunction<String> displayName;
    private final Consumer<Message> hide;
    private final Consumer<Message> ask;

    public MessageBubble() { this(-1, id -> "Member #" + id, null, null); }

    public MessageBubble(long currentUserId, LongFunction<String> displayName,
                         Consumer<Message> hide, Consumer<Message> ask) {
        this.currentUserId = currentUserId;
        this.displayName = displayName;
        this.hide = hide;
        this.ask = ask;
    }

    @Override protected void updateItem(Message message, boolean empty) {
        super.updateItem(message, empty);
        setText(null);
        getStyleClass().remove("message-bubble");
        if (empty || message == null) { setGraphic(null); setContextMenu(null); return; }

        boolean mine = message.getSenderId() == currentUserId;
        Label sender = new Label(mine ? "You" : displayName.apply(message.getSenderId()));
        sender.getStyleClass().addAll("small-heading", "message-sender");
        Label text = new Label(message.getContent());
        text.getStyleClass().add("message-text"); text.setWrapText(true);
        Label time = new Label(TIME.format(message.getSentAt()));
        time.getStyleClass().add("message-time");
        VBox bubble = new VBox(4, sender, text, time);
        bubble.getStyleClass().add(mine ? "message-mine" : "message-theirs");
        HBox row = new HBox(bubble);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        HBox.setHgrow(bubble, Priority.NEVER);
        row.setMaxWidth(Double.MAX_VALUE);
        setGraphic(row); getStyleClass().add("message-bubble");

        if (hide != null) {
            MenuItem hideItem = new MenuItem("Hide Message"); hideItem.setOnAction(event -> hide.accept(message));
            MenuItem assistantItem = new MenuItem("Ask AI about this message"); assistantItem.setOnAction(event -> ask.accept(message));
            setContextMenu(new ContextMenu(hideItem, assistantItem));
        }
    }
}
