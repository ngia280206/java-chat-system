package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.SpamReport;
import javafx.scene.control.ListCell;

public final class ReportCell extends ListCell<SpamReport> {

    @Override
    protected void updateItem(SpamReport r, boolean e) {
        super.updateItem(r, e);
        setText(null);
        setGraphic(e || r == null ? null : new javafx.scene.layout.HBox(12,
                CellLayout.field("RP-" + r.getId(), 120, "list-title"),
                CellLayout.field(r.getReason(), 420, "body-text"),
                CellLayout.field(r.getStatus().name(), 140,
                        r.getStatus() == com.chatsystem.shared.model.status.ReportStatus.PENDING ? "pill-warning" : "pill-success")));
    }
}
