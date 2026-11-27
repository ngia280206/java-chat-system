package com.chatsystem.admin.ui.cell;

import com.chatsystem.shared.model.ActivityEvent;
import javafx.scene.control.ListCell;

public final class ActivityEventCell extends ListCell<ActivityEvent> {
    @Override protected void updateItem(ActivityEvent event,boolean empty) {
        super.updateItem(event,empty);setText(null);
        setGraphic(empty||event==null?null:RowLayout.identity(
                event.type().name().toLowerCase().replace('_',' '),
                event.occurredAt().toString().replace('T',' ').replace("Z",""),260));
    }
}
