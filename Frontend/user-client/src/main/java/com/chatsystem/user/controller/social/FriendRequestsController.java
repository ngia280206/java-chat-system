package com.chatsystem.user.controller.social;

import com.chatsystem.shared.model.FriendRequest;
import com.chatsystem.shared.service.SocialService;
import com.chatsystem.shared.validation.ValidationResult;
import com.chatsystem.user.navigation.*;
import com.chatsystem.user.ui.cell.FriendRequestCell;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.util.function.LongFunction;

public final class FriendRequestsController {
    private final SocialService social;
    private final UserNavigator navigator;
    private FriendRequest pendingCancellation;
    @FXML private ListView<FriendRequest> incomingList;
    @FXML private ListView<FriendRequest> outgoingList;
    @FXML private Label requestFeedbackLabel;
    @FXML private HBox requestConfirmationBar;

    public FriendRequestsController(SocialService social, UserNavigator navigator) { this.social = social; this.navigator = navigator; }
    @FXML private void initialize() {
        incomingList.setItems(social.getIncomingRequests()); outgoingList.setItems(social.getOutgoingRequests());
        incomingList.setCellFactory(ignored -> new FriendRequestCell("Accept", request -> apply(social.acceptRequest(request.getId()), "Request accepted"), "Decline", request -> apply(social.rejectRequest(request.getId()), "Request declined")));
        outgoingList.setCellFactory(ignored -> new FriendRequestCell("Cancel request", this::requestCancel, null, null));
    }
    @FXML private void accept() { run(incomingList, social::acceptRequest, "Request accepted"); }
    @FXML private void decline() { run(incomingList, social::rejectRequest, "Request declined"); }
    @FXML private void requestCancel() {
        requestCancel(outgoingList.getSelectionModel().getSelectedItem());
    }
    private void requestCancel(FriendRequest request) {
        pendingCancellation = request;
        if (pendingCancellation == null) { feedback("Choose a sent request"); return; }
        requestConfirmationBar.setManaged(true); requestConfirmationBar.setVisible(true);
    }
    @FXML private void confirmCancel() { if (pendingCancellation != null) apply(social.cancelRequest(pendingCancellation.getId()), "Request cancelled"); cancelDecision(); }
    @FXML private void cancelDecision() { pendingCancellation = null; requestConfirmationBar.setManaged(false); requestConfirmationBar.setVisible(false); }
    @FXML private void back() { navigator.navigate(ViewRequest.of(UserRoute.FRIENDS)); }
    private void run(ListView<FriendRequest> list, LongFunction<ValidationResult<Void>> action, String success) {
        FriendRequest request = list.getSelectionModel().getSelectedItem(); if (request == null) { feedback("Choose a request"); return; }
        apply(action.apply(request.getId()), success);
    }
    private void apply(ValidationResult<Void> result, String success) { feedback(result.isSuccess() ? success : result.errors().get(0).message()); }
    private void feedback(String text) { requestFeedbackLabel.setText(text); }
}
