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
    @FXML
    private ListView<GroupInvitation> invitationList;
    @FXML
    private Label invitationFeedbackLabel;

    public GroupInvitationsController(GroupService g, UserNavigator n) {
        groups = g;
        nav = n;
    }

    @FXML
    private void initialize() {
        invitationList.setItems(groups.getInvitations());
        invitationList.setCellFactory(x -> new GroupInvitationCell());
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
        if (i == null) {
            invitationFeedbackLabel.setText("Choose an invitation");
            return;
        }
        var r = accept ? groups.acceptInvitation(i.getId()) : groups.rejectInvitation(i.getId());
        invitationFeedbackLabel.setText(r.isSuccess() ? (accept ? "Joined group" : "Invitation declined") : r.errors().get(0).message());
    }
}
