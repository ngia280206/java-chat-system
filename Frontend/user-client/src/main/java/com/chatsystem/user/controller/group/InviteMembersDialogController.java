package com.chatsystem.user.controller.group;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.service.*;
import com.chatsystem.user.controller.chat.NewConversationDialogController;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class InviteMembersDialogController {

    private final GroupService groups;
    private final AdminService directory;
    private long id;
    @FXML
    private ComboBox<UserAccount> memberChoice;
    @FXML
    private Label feedbackLabel;

    public InviteMembersDialogController(GroupService g, AdminService d) {
        groups = g;
        directory = d;
    }

    @FXML
    private void initialize() {
        memberChoice.setItems(directory.getUsers());
        memberChoice.setConverter(NewConversationDialogController.names());
    }

    public void present(long groupId) {
        id = groupId;
    }

    @FXML
    private void submit() {
        var member = memberChoice.getValue();
        if (member == null) {
            feedbackLabel.setText("Choose a member");
            return;
        }
        var r = groups.inviteMember(id, member.getId());
        if (r.isSuccess()) {
            memberChoice.getScene().getWindow().hide(); 
        }else {
            feedbackLabel.setText(r.errors().get(0).message());
    
        }}

    @FXML
    private void cancel() {
        memberChoice.getScene().getWindow().hide();
    }
}
