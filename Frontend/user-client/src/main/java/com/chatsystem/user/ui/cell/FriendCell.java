package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.UserAccount;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;

public final class FriendCell extends ListCell<UserAccount> {
    private final Consumer<UserAccount> message, block, remove, createGroup;
    public FriendCell() { this(ignored->{}, ignored->{}); }
    public FriendCell(Consumer<UserAccount> message, Consumer<UserAccount> block) {
        this(message,block,ignored->{},ignored->{});
    }
    public FriendCell(Consumer<UserAccount> message, Consumer<UserAccount> block,
                      Consumer<UserAccount> remove, Consumer<UserAccount> createGroup) {
        this.message=message;this.block=block;this.remove=remove;this.createGroup=createGroup;
    }
    @Override protected void updateItem(UserAccount user, boolean empty) {
        super.updateItem(user,empty);
        if (empty || user==null) {setText(null);setGraphic(null);return;}
        Label identity = new Label(user.getDisplayName()+"  @"+user.getUsername());
        Label presence = new Label(user.isOnline()?"● Online":"○ Offline");
        presence.setStyle(user.isOnline()?"-fx-text-fill: #68D9A4;":"-fx-text-fill: #A0B0C3;");
        HBox.setHgrow(identity,Priority.ALWAYS);
        HBox row = new HBox(9, identity, presence,action("Message",()->message.accept(user)),
                action("Create group",()->createGroup.accept(user)),action("Unfriend",()->remove.accept(user)),
                action("Block",()->block.accept(user)));
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        setText(null);setGraphic(row);
    }
    private Button action(String text,Runnable task) {
        Button button=new Button(text);button.getStyleClass().add("friend-row-action");
        button.setOnAction(e->task.run());return button;
    }
}
