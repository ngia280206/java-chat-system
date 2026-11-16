package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.service.*;
import com.chatsystem.shared.validation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.time.*;
import java.util.*;

public final class InMemoryAdminService implements AdminService {
    private final MockDataStore store;
    private final ObservableList<Conversation> groups = FXCollections.observableArrayList();
    private final ObservableList<Conversation> groupsView = FXCollections.unmodifiableObservableList(groups);
    private final ObservableList<DailyActivity> trend = FXCollections.observableArrayList();
    private final ObservableList<DailyActivity> trendView = FXCollections.unmodifiableObservableList(trend);
    private final ReadOnlyObjectWrapper<DashboardMetrics> metrics = new ReadOnlyObjectWrapper<>(this, "metrics");
    private Instant from = Instant.MIN;
    private Instant until = Instant.MAX;

    public InMemoryAdminService(MockDataStore store) {
        this.store = Objects.requireNonNull(store);
        InvalidationListener refresh = ignored -> refresh();
        store.getUsers().addListener(refresh);
        store.getConversations().addListener(refresh);
        store.getMembers().addListener(refresh);
        store.getMessages().addListener(refresh);
        store.getReports().addListener(refresh);
        store.getLoginEvents().addListener(refresh);
        store.getActivityEvents().addListener(refresh);
        refresh();
    }

    @Override public ObservableList<UserAccount> getUsers() { return store.getUsers(); }
    @Override public ObservableList<Conversation> getGroups() { return groupsView; }
    @Override public ObservableList<SpamReport> getReports() { return store.getReports(); }
    @Override public Optional<UserAccount> getUser(long userId) {
        return store.getUsers().stream().filter(u -> u.getId() == userId).findFirst();
    }
    @Override public Optional<Conversation> getGroup(long groupId) {
        return store.getConversations().stream().filter(c -> c.getId() == groupId && c.getType() == ConversationType.GROUP).findFirst();
    }
    @Override public Optional<SpamReport> getReport(long reportId) {
        return store.getReports().stream().filter(r -> r.getId() == reportId).findFirst();
    }
    @Override public Optional<Message> getMessage(long messageId) {
        return store.getMessages().stream().filter(m -> m.getId() == messageId).findFirst();
    }

    @Override public List<UserAccount> searchUsers(String query, UserStatus status, UserSort sort, int page, int pageSize) {
        String text = MockServiceQueries.normalize(query);
        Comparator<UserAccount> order = switch (Objects.requireNonNull(sort)) {
            case NAME -> Comparator.comparing(u -> MockServiceQueries.normalize(u.getDisplayName()));
            case NEWEST -> Comparator.comparing(UserAccount::getCreatedAt).reversed();
            case RECENT_LOGIN -> Comparator.comparing((UserAccount u) -> lastLogin(u.getId())).reversed();
        };
        return MockServiceQueries.page(store.getUsers().stream().filter(u -> status == null || u.getStatus() == status)
                .filter(u -> MockServiceQueries.normalize(u.getDisplayName()).contains(text)
                        || MockServiceQueries.normalize(u.getUsername()).contains(text)
                        || MockServiceQueries.normalize(u.getEmail()).contains(text)
                        || Long.toString(u.getId()).equals(text))
                .sorted(order.thenComparingLong(UserAccount::getId)).toList(), page, pageSize);
    }

    @Override public List<Conversation> searchGroups(String query, GroupSort sort, int page, int pageSize) {
        String text = MockServiceQueries.normalize(query);
        Comparator<Conversation> order = switch (Objects.requireNonNull(sort)) {
            case NAME -> Comparator.comparing(c -> MockServiceQueries.normalize(c.getName()));
            case NEWEST -> Comparator.comparing(Conversation::getCreatedAt).reversed();
            case MEMBER_COUNT -> Comparator.comparingLong((Conversation c) -> getGroupMembers(c.getId()).size()).reversed();
        };
        return MockServiceQueries.page(groups.stream().filter(c -> MockServiceQueries.normalize(c.getName()).contains(text)
                        || MockServiceQueries.normalize(c.getDescription()).contains(text)
                        || getUser(c.getCreatedBy()).map(owner -> MockServiceQueries.normalize(owner.getDisplayName()).contains(text)
                                || MockServiceQueries.normalize(owner.getUsername()).contains(text)).orElse(false))
                .sorted(order.thenComparingLong(Conversation::getId)).toList(), page, pageSize);
    }

    @Override public List<SpamReport> searchReports(String query, ReportStatus status, ReportService.ReportSort sort, int page, int pageSize) {
        return MockServiceQueries.reports(store.getReports(), store.getUsers(), query, status, sort, page, pageSize);
    }

    @Override public List<SpamReport> searchReportsForConversation(long conversationId, String query, ReportStatus status,
                                                                   ReportService.ReportSort sort, int page, int pageSize) {
        if (getGroup(conversationId).isEmpty()) return List.of();
        List<Long> messageIds = store.getMessages().stream().filter(m -> m.getConversationId() == conversationId)
                .map(Message::getId).toList();
        List<SpamReport> scoped = store.getReports().stream().filter(r -> messageIds.contains(r.getMessageId())).toList();
        return MockServiceQueries.reports(scoped, store.getUsers(), query, status, sort, page, pageSize);
    }

    @Override public List<ConversationMember> getGroupMembers(long groupId) {
        if (getGroup(groupId).isEmpty()) return List.of();
        return store.getMembers().stream().filter(m -> m.getConversationId() == groupId && m.getLeftAt() == null)
                .sorted(Comparator.comparingLong(ConversationMember::getUserId)).toList();
    }

    @Override public List<LoginEvent> getLoginHistory(long userId) {
        if (getUser(userId).isEmpty()) return List.of();
        return store.getLoginEvents().stream().filter(e -> e.userId() == userId)
                .sorted(Comparator.comparing(LoginEvent::loggedInAt).thenComparingLong(LoginEvent::id).reversed()).toList();
    }

    @Override public List<ActivityEvent> getUserActivity(long userId) {
        if (getUser(userId).isEmpty()) return List.of();
        return store.getActivityEvents().stream().filter(e -> e.userId() == userId).sorted(activityOrder()).toList();
    }

    @Override public List<ActivityEvent> getGroupActivity(long groupId) {
        if (getGroup(groupId).isEmpty()) return List.of();
        return store.getActivityEvents().stream().filter(e -> Long.valueOf(groupId).equals(e.conversationId()))
                .sorted(activityOrder()).toList();
    }

    @Override public ValidationResult<Void> lockUser(long userId) { return changeStatus(userId, UserStatus.ACTIVE, UserStatus.LOCKED); }
    @Override public ValidationResult<Void> unlockUser(long userId) { return changeStatus(userId, UserStatus.LOCKED, UserStatus.ACTIVE); }

    private ValidationResult<Void> changeStatus(long userId, UserStatus expected, UserStatus next) {
        UserAccount user = getUser(userId).orElse(null);
        if (user == null || user.getStatus() != expected) return failure("This account is no longer available for that status change");
        user.setStatus(next);
        return ValidationResult.success(null);
    }

    @Override public ValidationResult<Void> reviewReport(long reportId, String notes) {
        return resolve(reportId, notes, ReportStatus.REVIEWED, "");
    }
    @Override public ValidationResult<Void> dismissReport(long reportId, String notes) {
        return resolve(reportId, notes, ReportStatus.DISMISSED, "");
    }
    @Override public ValidationResult<Void> warnUser(long reportId, String notes) {
        return resolve(reportId, notes, ReportStatus.REVIEWED, "Warning: ");
    }
    @Override public ValidationResult<Void> suspendUser(long reportId, String notes) {
        SpamReport report = pendingReport(reportId);
        if (report == null || !validNotes(notes)) return failure("Choose a pending report and enter review notes");
        UserAccount target = getUser(report.getReportedUserId()).orElse(null);
        if (target == null || target.getStatus() == UserStatus.DELETED) return failure("The reported account is unavailable");
        target.setStatus(UserStatus.LOCKED);
        report.review(ReportStatus.REVIEWED, "Suspension: " + notes.strip());
        return ValidationResult.success(null);
    }

    private ValidationResult<Void> resolve(long reportId, String notes, ReportStatus status, String decision) {
        SpamReport report = pendingReport(reportId);
        if (report == null || !validNotes(notes)) return failure("Choose a pending report and enter review notes");
        report.review(status, decision + notes.strip());
        return ValidationResult.success(null);
    }

    private SpamReport pendingReport(long reportId) {
        return getReport(reportId).filter(r -> r.getStatus() == ReportStatus.PENDING).orElse(null);
    }
    private static boolean validNotes(String notes) { return notes != null && !notes.isBlank(); }

    @Override public DashboardMetrics getMetrics() { return metrics.get(); }
    @Override public ReadOnlyObjectProperty<DashboardMetrics> metricsProperty() { return metrics.getReadOnlyProperty(); }
    @Override public ObservableList<DailyActivity> getActivityTrend() { return trendView; }
    @Override public ValidationResult<Void> setDateRange(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            return failure("Choose a start before the end of the date range");
        }
        from = fromInclusive;
        until = toExclusive;
        refresh();
        return ValidationResult.success(null);
    }

    private void refresh() {
        groups.setAll(store.getConversations().stream().filter(c -> c.getType() == ConversationType.GROUP)
                .sorted(Comparator.comparingLong(Conversation::getId)).toList());
        metrics.set(new DashboardMetrics(store.getUsers().size(),
                store.getUsers().stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).count(),
                store.getUsers().stream().filter(u -> u.getStatus() == UserStatus.LOCKED).count(), groups.size(),
                store.getReports().stream().filter(r -> r.getStatus() == ReportStatus.PENDING).count(),
                store.getMessages().stream().filter(m -> inRange(m.getSentAt())).count(),
                store.getLoginEvents().stream().filter(e -> inRange(e.loggedInAt())).count(),
                store.getActivityEvents().stream().filter(e -> inRange(e.occurredAt())).map(ActivityEvent::userId).distinct().count()));
        Map<LocalDate, long[]> days = new TreeMap<>();
        store.getActivityEvents().stream().filter(e -> inRange(e.occurredAt())).forEach(e -> {
            long[] counts = days.computeIfAbsent(e.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(), ignored -> new long[2]);
            counts[e.type() == ActivityType.APP_OPEN ? 0 : 1]++;
        });
        trend.setAll(days.entrySet().stream().map(e -> new DailyActivity(e.getKey(), e.getValue()[0], e.getValue()[1])).toList());
    }

    private boolean inRange(Instant time) { return !time.isBefore(from) && time.isBefore(until); }
    private Instant lastLogin(long userId) {
        return store.getLoginEvents().stream().filter(e -> e.userId() == userId).map(LoginEvent::loggedInAt)
                .max(Comparator.naturalOrder()).orElse(Instant.MIN);
    }
    private static Comparator<ActivityEvent> activityOrder() {
        return Comparator.comparing(ActivityEvent::occurredAt).thenComparingLong(ActivityEvent::id).reversed();
    }
    private static <T> ValidationResult<T> failure(String message) {
        return ValidationResult.failure(List.of(ValidationError.global(message)));
    }
}
