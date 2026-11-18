package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.FriendRequest;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;

public final class FriendRequestCell extends ListCell<FriendRequest> {

    private final String primaryText;
    private final Consumer<FriendRequest> primary;
    private final String secondaryText;
    private final Consumer<FriendRequest> secondary;

    public FriendRequestCell() {
        this("", ignored -> {
        }, null, null);
    }

    public FriendRequestCell(String primaryText, Consumer<FriendRequest> primary, String secondaryText, Consumer<FriendRequest> secondary) {
        this.primaryText = primaryText;
        this.primary = primary;
        this.secondaryText = secondaryText;
        this.secondary = secondary;
    }

    @Override
    protected void updateItem(FriendRequest request, boolean empty) {
        super.updateItem(request, empty);
        if (empty || request == null) {
            setText(null);
            setGraphic(null);
            return;
        }
        Label label = new Label("Request #" + request.getId() + " · account " + (request.getSenderId() == 1 ? request.getReceiverId() : request.getSenderId()));
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox row = new HBox(10, label, action(primaryText, () -> primary.accept(request)));
        if (secondary != null) {
            row.getChildren().add(action(secondaryText, () -> secondary.accept(request)));
        }
        setText(null);
        setGraphic(row);
    }

    private Button action(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("request-row-action");
        button.setOnAction(event -> action.run());
        return button;
    }
}
