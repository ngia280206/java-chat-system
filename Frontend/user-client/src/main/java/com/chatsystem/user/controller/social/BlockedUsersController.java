package com.chatsystem.user.controller.social;

import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.service.SocialService;
import com.chatsystem.user.navigation.*;
import com.chatsystem.user.ui.cell.BlockedUserCell;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

public final class BlockedUsersController {
    private final SocialService social; private final UserNavigator navigator; private UserAccount pending;
    @FXML private ListView<UserAccount> blockedList; @FXML private Label blockedFeedbackLabel; @FXML private HBox unblockConfirmationBar;
    public BlockedUsersController(SocialService social, UserNavigator navigator) { this.social = social; this.navigator = navigator; }
    @FXML private void initialize() { blockedList.setItems(social.getBlockedUsers()); blockedList.setCellFactory(ignored -> new BlockedUserCell(this::requestUnblock)); }
    @FXML private void requestUnblock() { requestUnblock(blockedList.getSelectionModel().getSelectedItem()); }
    private void requestUnblock(UserAccount user) { pending = user; if (pending == null) { feedback("Choose a blocked user"); return; } unblockConfirmationBar.setManaged(true); unblockConfirmationBar.setVisible(true); }
    @FXML private void confirmUnblock() { if (pending != null) { var result = social.unblockUser(pending.getId()); feedback(result.isSuccess() ? "User unblocked" : result.errors().get(0).message()); } cancelUnblock(); }
    @FXML private void cancelUnblock() { pending = null; unblockConfirmationBar.setManaged(false); unblockConfirmationBar.setVisible(false); }
    @FXML private void back() { navigator.navigate(ViewRequest.of(UserRoute.FRIENDS)); }
    private void feedback(String text) { blockedFeedbackLabel.setText(text); }
}
