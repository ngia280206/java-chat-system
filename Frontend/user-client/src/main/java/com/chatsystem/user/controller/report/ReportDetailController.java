package com.chatsystem.user.controller.report;

import com.chatsystem.shared.service.ReportService;
import com.chatsystem.user.navigation.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class ReportDetailController {

    private final ReportService reports;
    private final UserNavigator nav;
    @FXML
    private Label reportIdLabel, reportReasonLabel, reportStatusLabel, reportTargetLabel, reportProgressLabel, reportTimelineLabel;

    public ReportDetailController(ReportService r, UserNavigator n) {
        reports = r;
        nav = n;
    }

    public void present(long id) {
        var r = reports.getReports().stream().filter(x -> x.getId() == id).findFirst().orElse(null);
        if (r == null) {
            reportReasonLabel.setText("Report unavailable");
            return;
        }
        reportIdLabel.setText("Report #" + r.getId());
        reportReasonLabel.setText(r.getReason());
        reportStatusLabel.textProperty().bind(r.statusProperty().asString());
        reportTargetLabel.setText("Account #" + r.getReportedUserId() + " · message #" + r.getMessageId());
        reportProgressLabel.textProperty().bind(r.statusProperty().asString("Progress: %s"));
        reportTimelineLabel.setText("Submitted " + r.getCreatedAt() + " · Next: moderation review");
    }

    @FXML
    private void back() {
        nav.navigate(ViewRequest.of(UserRoute.SPAM_REPORTS));
    }
}
