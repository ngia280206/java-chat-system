package com.chatsystem.shared.service;

import com.chatsystem.shared.model.SpamReport;
import com.chatsystem.shared.model.status.ReportStatus;
import com.chatsystem.shared.validation.ValidationResult;
import javafx.collections.ObservableList;
import java.util.List;

/** Current active account's report history; reporting never changes the target's status. */
public interface ReportService {
    enum ReportSort { NEWEST, OLDEST }
    ObservableList<SpamReport> getReports();
    ValidationResult<SpamReport> submitReport(long messageId, long reportedUserId, String reason);
    /** Snapshot of case ID, reported user, reason/notes matches. Null status means all; pages are zero-based. */
    List<SpamReport> searchReports(String query, ReportStatus status, ReportSort sort, int page, int pageSize);
}
