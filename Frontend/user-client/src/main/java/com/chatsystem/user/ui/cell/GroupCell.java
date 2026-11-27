package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.Conversation;
import javafx.scene.control.ListCell;

public final class GroupCell extends ListCell<Conversation> {

    @Override
    protected void updateItem(Conversation g, boolean e) {
        super.updateItem(g, e);
        setText(null);
        setGraphic(e || g == null ? null : CellLayout.identity(g.getName(), g.getDescription()));
    }
}
