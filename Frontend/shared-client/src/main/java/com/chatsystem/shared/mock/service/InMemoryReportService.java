package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.service.ReportService;
import com.chatsystem.shared.session.UserSession;
import com.chatsystem.shared.validation.*;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.*;

public final class InMemoryReportService implements ReportService {
    private final MockDataStore store;
    private final UserSession session;
    private final ObservableList<SpamReport> reports = FXCollections.observableArrayList();
    private final ObservableList<SpamReport> reportsView = FXCollections.unmodifiableObservableList(reports);

    public InMemoryReportService(MockDataStore store, UserSession session) {
        this.store = Objects.requireNonNull(store);
        this.session = Objects.requireNonNull(session);
        InvalidationListener refresh = ignored -> refresh();
        session.currentUserProperty().addListener(refresh);
        store.getUsers().addListener(refresh);
        store.getReports().addListener(refresh);
        refresh();
    }

    @Override public ObservableList<SpamReport> getReports() { return reportsView; }

    @Override public ValidationResult<SpamReport> submitReport(long messageId, long reportedUserId, String reason) {
        UserAccount actor = actor();
        Message message = store.getMessages().stream().filter(m -> m.getId() == messageId).findFirst().orElse(null);
        if (actor == null || message == null || reportedUserId == actor.getId() || message.getSenderId() != reportedUserId
                || store.getUsers().stream().noneMatch(u -> u.getId() == reportedUserId)
                || !store.isMockMessageVisible(actor.getId(), message)) {
            return ValidationResult.failure(List.of(ValidationError.global("Choose another user's visible message to report")));
        }
        if (reason == null || reason.isBlank()) {
            return ValidationResult.failure(List.of(ValidationError.field("reason", "Report reason is required")));
        }
        return ValidationResult.success(store.createMockReport(actor.getId(), reportedUserId, messageId, reason.strip()));
    }

    @Override public List<SpamReport> searchReports(String query, ReportStatus status, ReportSort sort, int page, int pageSize) {
        return MockServiceQueries.reports(reportsView, store.getUsers(), query, status, sort, page, pageSize);
    }

    private void refresh() {
        UserAccount actor = actor();
        reports.setAll(store.getReports().stream().filter(r -> actor != null && r.getReporterId() == actor.getId())
                .sorted(Comparator.comparing(SpamReport::getCreatedAt).thenComparingLong(SpamReport::getId).reversed()).toList());
    }

    private UserAccount actor() {
        UserAccount actor = session.getCurrentUser();
        return actor != null && actor.getStatus() == UserStatus.ACTIVE && store.getUsers().contains(actor) ? actor : null;
    }
}
