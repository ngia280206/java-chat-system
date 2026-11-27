package com.chatsystem.admin.ui.cell;

import com.chatsystem.shared.model.ConversationMember;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.service.AdminService;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;

public final class AdminMemberCell extends ListCell<ConversationMember> {
    private final AdminService service;
    public AdminMemberCell(AdminService service) {this.service=service;}
    @Override protected void updateItem(ConversationMember member,boolean empty) {
        super.updateItem(member,empty);setText(null);
        if(empty||member==null){setGraphic(null);return;}
        HBox row=new HBox(12,
                RowLayout.field(service.getUser(member.getUserId()).map(UserAccount::getDisplayName).orElse("Unknown member"),350,"list-title"),
                RowLayout.field(member.getRole().name(),150,"pill"),
                RowLayout.field(member.getJoinedAt().toString().substring(0,10),240,"body-text"));
        row.getStyleClass().add("list-row");setGraphic(row);
    }
}
