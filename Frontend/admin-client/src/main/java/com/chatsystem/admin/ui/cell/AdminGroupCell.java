package com.chatsystem.admin.ui.cell;

import com.chatsystem.shared.model.Conversation;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.service.AdminService;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import java.util.function.LongConsumer;

public final class AdminGroupCell extends ListCell<Conversation> {
    private final AdminService service;
    private final LongConsumer open;
    public AdminGroupCell(AdminService service, LongConsumer open) { this.service=service; this.open=open; }
    @Override protected void updateItem(Conversation group, boolean empty) {
        super.updateItem(group,empty); setText(null);
        if(empty || group==null) {setGraphic(null);return;}
        Button inspect=new Button("Inspect");inspect.getStyleClass().add("small-button");
        inspect.setOnAction(event->open.accept(group.getId()));
        HBox row=new HBox(12,RowLayout.identity(group.getName(),group.getDescription(),340),
                RowLayout.identity(service.getUser(group.getCreatedBy()).map(UserAccount::getDisplayName).orElse("Unknown"),
                        group.getCreatedAt().toString().substring(0,10),300),
                RowLayout.field(Integer.toString(service.getGroupMembers(group.getId()).size()),110,"body-text"),inspect);
        row.getStyleClass().add("list-row");setGraphic(row);
    }
}
