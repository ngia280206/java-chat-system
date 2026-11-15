package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.UserAccount;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;

public final class FriendCell extends ListCell<UserAccount> {
    private final Consumer<UserAccount> message;
    private final Consumer<UserAccount> block;
    public FriendCell() { this(ignored -> {}, ignored -> {}); }
    public FriendCell(Consumer<UserAccount> message, Consumer<UserAccount> block) { this.message = message; this.block = block; }
    @Override protected void updateItem(UserAccount user, boolean empty) {
        super.updateItem(user, empty);
        if (empty || user == null) { setText(null); setGraphic(null); return; }
        Label identity = new Label(user.getDisplayName() + "  @" + user.getUsername()); HBox.setHgrow(identity, Priority.ALWAYS);
        Button messageButton = action("Message", () -> message.accept(user));
        Button blockButton = action("Block User", () -> block.accept(user));
        setText(null); setGraphic(new HBox(10, identity, messageButton, blockButton));
    }
    private Button action(String text, Runnable action) { Button button = new Button(text); button.getStyleClass().add("friend-row-action"); button.setOnAction(event -> action.run()); return button; }
}
