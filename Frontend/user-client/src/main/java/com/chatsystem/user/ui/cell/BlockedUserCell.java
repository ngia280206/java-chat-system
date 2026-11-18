package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.UserAccount;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;

public final class BlockedUserCell extends ListCell<UserAccount> {

    private final Consumer<UserAccount> unblock;

    public BlockedUserCell() {
        this(ignored -> {
        });
    }

    public BlockedUserCell(Consumer<UserAccount> unblock) {
        this.unblock = unblock;
    }

    @Override
    protected void updateItem(UserAccount user, boolean empty) {
        super.updateItem(user, empty);
        if (empty || user == null) {
            setText(null);
            setGraphic(null);
            return;
        }
        Label label = new Label(user.getDisplayName());
        HBox.setHgrow(label, Priority.ALWAYS);
        Button button = new Button("Unblock");
        button.getStyleClass().add("blocked-row-action");
        button.setOnAction(event -> unblock.accept(user));
        setText(null);
        setGraphic(new HBox(10, label, button));
    }
}
