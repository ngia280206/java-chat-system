package com.chatsystem.admin.ui.cell;
import com.chatsystem.shared.model.ConversationMember;import com.chatsystem.shared.service.AdminService;import javafx.scene.control.*;
public final class AdminMemberCell extends ListCell<ConversationMember>{private final AdminService service;public AdminMemberCell(AdminService s){service=s;}@Override protected void updateItem(ConversationMember m,boolean empty){super.updateItem(m,empty);setText(empty||m==null?null:service.getUser(m.getUserId()).map(u->u.getDisplayName()+"   "+m.getRole()+"   Joined "+m.getJoinedAt()).orElse("Unknown member"));}}
