package com.chatsystem.shared.service;

import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.validation.ValidationResult;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ObservableList;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Pre-authenticated local administrator. Query snapshots use zero-based pages and stable ID ties. */
public interface AdminService {
    enum UserSort { NAME, NEWEST, RECENT_LOGIN }
    enum GroupSort { NAME, NEWEST, MEMBER_COUNT }
    /** Account/group/queue totals are current; message/login/activity counts use the selected date range. */
    record DashboardMetrics(long totalUsers, long activeUsers, long lockedUsers, long totalGroups,
                            long pendingReports, long messageCount, long loginCount, long activeUsersInRange) { }
    record DailyActivity(LocalDate date, long appOpens, long messages) { }

    ObservableList<UserAccount> getUsers();
    ObservableList<Conversation> getGroups();
    ObservableList<SpamReport> getReports();
    Optional<UserAccount> getUser(long userId);
    Optional<Conversation> getGroup(long groupId);
    Optional<SpamReport> getReport(long reportId);
    List<UserAccount> searchUsers(String query, UserStatus status, UserSort sort, int page, int pageSize);
    List<Conversation> searchGroups(String query, GroupSort sort, int page, int pageSize);
    List<SpamReport> searchReports(String query, ReportStatus status, ReportService.ReportSort sort, int page, int pageSize);
    List<ConversationMember> getGroupMembers(long groupId);
    List<LoginEvent> getLoginHistory(long userId);
    List<ActivityEvent> getUserActivity(long userId);
    List<ActivityEvent> getGroupActivity(long groupId);
    ValidationResult<Void> lockUser(long userId);
    ValidationResult<Void> unlockUser(long userId);
    ValidationResult<Void> reviewReport(long reportId, String notes);
    ValidationResult<Void> dismissReport(long reportId, String notes);
    ValidationResult<Void> warnUser(long reportId, String notes);
    ValidationResult<Void> suspendUser(long reportId, String notes);
    DashboardMetrics getMetrics();
    ReadOnlyObjectProperty<DashboardMetrics> metricsProperty();
    ObservableList<DailyActivity> getActivityTrend();
    /** Inclusive start, exclusive end, UTC chart days. Invalid ranges leave current projections unchanged. */
    ValidationResult<Void> setDateRange(Instant fromInclusive, Instant toExclusive);
}
