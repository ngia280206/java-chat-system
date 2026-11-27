package com.chatsystem.user.controller.group;

import com.chatsystem.shared.model.GroupInvitation;
import com.chatsystem.shared.service.GroupService;
import com.chatsystem.user.navigation.*;
import com.chatsystem.user.ui.cell.GroupInvitationCell;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class GroupInvitationsController {

    private final GroupService groups;
    private final UserNavigator nav;
    private final com.chatsystem.shared.service.AdminService directory;
    @FXML private javafx.scene.layout.HBox invitationSelectionActions;
    @FXML
    private ListView<GroupInvitation> invitationList;
    @FXML
    private Label invitationFeedbackLabel;

    public GroupInvitationsController(GroupService g, UserNavigator n) {
        this(g,null,n);
    }
    public GroupInvitationsController(GroupService g, com.chatsystem.shared.service.AdminService directory, UserNavigator n) {
        groups = g;
        nav = n;
        this.directory=directory;
    }

    @FXML
    private void initialize() {
        com.chatsystem.user.ui.SelectionActions.showWhenSelected(invitationSelectionActions,invitationList);
        invitationList.setItems(groups.getInvitations());
        invitationList.setCellFactory(x -> new GroupInvitationCell(
                id -> directory==null?"Group #"+id:directory.getGroup(id).map(com.chatsystem.shared.model.Conversation::getName).orElse("Unknown group"),
                id -> directory==null?"Account #"+id:directory.getUser(id).map(com.chatsystem.shared.model.UserAccount::getDisplayName).orElse("Unknown account"),
                invitation -> respond(invitation,true), invitation -> respond(invitation,false)));
    }

    @FXML
    private void join() {
        run(true);
    }

    @FXML
    private void decline() {
        run(false);
    }

    @FXML
    private void back() {
        nav.navigate(ViewRequest.of(UserRoute.GROUP_MANAGEMENT));
    }

    private void run(boolean accept) {
        var i = invitationList.getSelectionModel().getSelectedItem();
        respond(i,accept);
    }
    private void respond(GroupInvitation i,boolean accept) {
        if (i == null) {
            invitationFeedbackLabel.setText("Choose an invitation");
            return;
        }
        var r = accept ? groups.acceptInvitation(i.getId()) : groups.rejectInvitation(i.getId());
        invitationFeedbackLabel.setText(r.isSuccess() ? (accept ? "Joined group" : "Invitation declined") : r.errors().get(0).message());
    }
}
