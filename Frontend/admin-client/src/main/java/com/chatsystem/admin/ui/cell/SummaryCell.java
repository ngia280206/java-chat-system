package com.chatsystem.admin.ui.cell;

import javafx.scene.control.ListCell;

public final class SummaryCell extends ListCell<String> {
    @Override protected void updateItem(String summary,boolean empty) {
        super.updateItem(summary,empty);setText(null);
        if(empty||summary==null){setGraphic(null);return;}
        int split=summary.indexOf(':');
        setGraphic(RowLayout.identity(split<0?summary:summary.substring(0,split),
                split<0?"":summary.substring(split+1).strip(),650));
    }
}
