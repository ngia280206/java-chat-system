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
        Label identity = CellLayout.field(user.getDisplayName(),200,"list-title");
        Label username = CellLayout.field("@"+user.getUsername(),180,"body-text");
        Label presence = CellLayout.field(user.isOnline()?"Online":"Offline",110,user.isOnline()?"pill-success":"pill");
        HBox row = new HBox(12, identity, username, presence,action("Message",()->message.accept(user)),
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
