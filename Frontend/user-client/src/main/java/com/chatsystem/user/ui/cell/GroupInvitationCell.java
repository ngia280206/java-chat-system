package com.chatsystem.user.ui.cell;

import com.chatsystem.shared.model.GroupInvitation;
import javafx.scene.control.ListCell;

public final class GroupInvitationCell extends ListCell<GroupInvitation> {
    private final java.util.function.LongFunction<String> groupName;
    private final java.util.function.LongFunction<String> inviterName;
    private java.util.function.Consumer<GroupInvitation> join, decline;
    public GroupInvitationCell() { this(id -> "Group #"+id,id -> "Account #"+id); }
    public GroupInvitationCell(java.util.function.LongFunction<String> groupName, java.util.function.LongFunction<String> inviterName) {
        this.groupName=groupName;this.inviterName=inviterName;
    }
    public GroupInvitationCell(java.util.function.LongFunction<String> groupName, java.util.function.LongFunction<String> inviterName,
                               java.util.function.Consumer<GroupInvitation> join, java.util.function.Consumer<GroupInvitation> decline) {
        this(groupName,inviterName);this.join=join;this.decline=decline;
    }

    @Override
    protected void updateItem(GroupInvitation i, boolean e) {
        super.updateItem(i, e);
        setText(null);
        if(e || i==null) {setGraphic(null);return;}
        var identity=CellLayout.identity(groupName.apply(i.getConversationId()),"Invited by " + inviterName.apply(i.getInviterId()));
        if(join==null) {setGraphic(identity);return;}
        javafx.scene.layout.HBox.setHgrow(identity,javafx.scene.layout.Priority.ALWAYS);
        var accept=new javafx.scene.control.Button("Join");accept.getStyleClass().add("primary-button");accept.setOnAction(event->join.accept(i));
        var reject=new javafx.scene.control.Button("Decline");reject.getStyleClass().add("small-button");reject.setOnAction(event->decline.accept(i));
        var row=new javafx.scene.layout.HBox(12,identity,accept,reject);row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);setGraphic(row);
    }
}
