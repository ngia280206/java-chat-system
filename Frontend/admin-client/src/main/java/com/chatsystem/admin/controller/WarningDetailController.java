package com.chatsystem.admin.controller;

import com.chatsystem.admin.navigation.*;
import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.service.AdminService;
import com.chatsystem.shared.validation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.*;

public final class WarningDetailController implements DisposableController {
    public enum Decision { DISMISS, WARN, SUSPEND }
    private final AdminService service; private final AdminNavigator navigator; private final javafx.beans.InvalidationListener reportListener=o->sync(); private long reportId; private SpamReport report; private boolean unavailable;
    @FXML private Label caseLabel,statusLabel,reportedUserLabel,reporterLabel,reasonLabel,messageLabel,validationLabel; @FXML private TextArea notesArea;
    @FXML private Button backButton,dismissButton,warnButton,suspendButton;
    public WarningDetailController(AdminService service,AdminNavigator navigator){this.service=Objects.requireNonNull(service);this.navigator=Objects.requireNonNull(navigator);service.getReports().addListener(reportListener);}
    @FXML private void initialize(){backButton.setOnAction(e->navigator.navigate(ViewRequest.of(AdminRoute.MODERATION)));dismissButton.setOnAction(e->confirm(Decision.DISMISS));warnButton.setOnAction(e->confirm(Decision.WARN));suspendButton.setOnAction(e->confirm(Decision.SUSPEND));}
    public void present(long id){reportId=id;sync();}
    public SpamReport report(){return report;} public boolean isUnavailable(){return unavailable;} public String messageText(){return report==null?"":service.getMessage(report.getMessageId()).map(Message::getContent).orElse("Reported message is unavailable");}
    public ValidationResult<Void> decide(Decision decision,String notes,boolean confirmed){if(!confirmed)return failure("Decision cancelled; no changes were made");SpamReport current=service.getReport(reportId).orElse(null);if(current==null||current.getStatus()!=ReportStatus.PENDING){sync();unavailable=true;return failure("This report is no longer pending or available");}ValidationResult<Void> result=switch(decision){case DISMISS->service.dismissReport(reportId,notes);case WARN->service.warnUser(reportId,notes);case SUSPEND->service.suspendUser(reportId,notes);};report=current;sync();return result;}
    private void confirm(Decision decision){String action=switch(decision){case DISMISS->"dismiss this report";case WARN->"warn the reported user";case SUSPEND->"suspend the reported user";};Alert alert=new Alert(Alert.AlertType.CONFIRMATION,"Confirm that you want to "+action+"?",ButtonType.OK,ButtonType.CANCEL);alert.setHeaderText("Confirm moderation decision");boolean confirmed=alert.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;var result=decide(decision,notesArea.getText(),confirmed);validationLabel.setText(result.isSuccess()?"Decision recorded.":result.errors().get(0).message());}
    private void sync(){report=service.getReport(reportId).orElse(null);unavailable=report==null;if(caseLabel==null)return;if(unavailable){caseLabel.setText("CASE WR-"+reportId);statusLabel.setText("Unavailable");reasonLabel.setText("This report is no longer available.");reportedUserLabel.setText("Unavailable");reporterLabel.setText("Unavailable");messageLabel.setText("Evidence unavailable");notesArea.clear();notesArea.setEditable(false);setActionsDisabled(true);return;}render();}
    private void render(){caseLabel.setText("CASE WR-"+report.getId()+"  •  MESSAGE "+report.getMessageId());statusLabel.setText(report.getStatus().name());reasonLabel.setText(report.getReason());reportedUserLabel.setText(service.getUser(report.getReportedUserId()).map(UserAccount::getDisplayName).orElse("Unavailable user"));reporterLabel.setText(service.getUser(report.getReporterId()).map(UserAccount::getDisplayName).orElse("Unavailable reporter"));messageLabel.setText(messageText());boolean pending=report.getStatus()==ReportStatus.PENDING;notesArea.setEditable(pending);if(!pending)notesArea.setText(report.getReviewNotes());setActionsDisabled(!pending);}
    private void setActionsDisabled(boolean disabled){dismissButton.setDisable(disabled);warnButton.setDisable(disabled);suspendButton.setDisable(disabled);}
    public void dispose(){service.getReports().removeListener(reportListener);}
    private static ValidationResult<Void> failure(String message){return ValidationResult.failure(List.of(new ValidationError("moderation",message)));}
}
