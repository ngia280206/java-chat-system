package com.chatsystem.admin.ui.cell;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.UserStatus;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import java.util.function.LongConsumer;

public final class AdminUserCell extends ListCell<UserAccount> {
    private final LongConsumer open;
    public AdminUserCell(LongConsumer open) { this.open = open; }
    @Override protected void updateItem(UserAccount user, boolean empty) {
        super.updateItem(user, empty); setText(null);
        if (empty || user == null) { setGraphic(null); return; }
        Button inspect = new Button("View details"); inspect.getStyleClass().add("small-button");
        inspect.setOnAction(event -> open.accept(user.getId()));
        Label status = RowLayout.field(user.getStatus().name(),160,
                user.getStatus()==UserStatus.ACTIVE ? "pill-success" : "pill-danger");
        status.textProperty().bind(user.statusProperty().asString());
        var identity = RowLayout.identity(user.getDisplayName(),user.getEmail(),400);
        ((Label)identity.getChildren().get(0)).textProperty().bind(user.displayNameProperty());
        HBox row = new HBox(12, RowLayout.field("#"+user.getId(),70,"body-text"),identity,status,inspect);
        row.getStyleClass().add("list-row"); setGraphic(row);
    }
}
