package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.Message;
import javafx.scene.control.*;
import java.util.function.Consumer;

public final class MessageBubble extends ListCell<Message> {
    private final Consumer<Message> hide;
    private final Consumer<Message> askAssistant;
    public MessageBubble() { this(null, null); }
    public MessageBubble(Consumer<Message> hide, Consumer<Message> askAssistant) { this.hide = hide; this.askAssistant = askAssistant; }
    @Override protected void updateItem(Message message, boolean empty) {
        super.updateItem(message, empty); setText(empty || message == null ? null : message.getContent());
        getStyleClass().removeAll("message-bubble");
        if (empty || message == null) { setContextMenu(null); return; }
        getStyleClass().add("message-bubble");
        if (hide != null) {
            MenuItem hideItem = new MenuItem("Hide Message"); hideItem.setOnAction(event -> hide.accept(message));
            MenuItem assistantItem = new MenuItem("Ask AI about this message"); assistantItem.setOnAction(event -> askAssistant.accept(message));
            setContextMenu(new ContextMenu(hideItem, assistantItem));
        }
    }
}
