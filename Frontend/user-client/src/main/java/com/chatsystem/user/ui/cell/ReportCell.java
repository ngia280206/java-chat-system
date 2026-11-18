package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.SpamReport;
import javafx.scene.control.ListCell;

public final class ReportCell extends ListCell<SpamReport> {

    @Override
    protected void updateItem(SpamReport r, boolean e) {
        super.updateItem(r, e);
        setText(e || r == null ? null : "Report #" + r.getId() + " · " + r.getStatus() + "\n" + r.getReason());
    }
}
