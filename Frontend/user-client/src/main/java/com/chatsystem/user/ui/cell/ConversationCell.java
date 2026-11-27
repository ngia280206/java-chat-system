package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.Conversation;
import javafx.scene.control.ListCell;

public final class ConversationCell extends ListCell<Conversation> {

    @Override
    protected void updateItem(Conversation item, boolean empty) {
        super.updateItem(item, empty);
        setText(null);
        setGraphic(empty || item == null ? null : CellLayout.identity(
                item.getName().isBlank() ? "Direct conversation" : item.getName(), item.getLastMessagePreview()));
    }
}
