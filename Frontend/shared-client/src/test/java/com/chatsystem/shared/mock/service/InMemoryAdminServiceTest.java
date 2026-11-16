package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.*;
import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryAdminServiceTest {
    private MockDataStore store;
    private AdminService admin;

    @BeforeEach void setUp() { store = MockFixtures.createStore(); admin = new InMemoryAdminService(store); }

    @Test void reviewAndDismissResolveQueueWithoutChangingAccountStatus() {
        SpamReport pending = admin.getReport(501).orElseThrow();
        assertTrue(admin.reviewReport(501, "Context reviewed").isSuccess());
        assertEquals(ReportStatus.REVIEWED, pending.getStatus());
        assertEquals("Context reviewed", pending.getReviewNotes());
        assertEquals(UserStatus.LOCKED, admin.getUser(3).orElseThrow().getStatus());
        assertEquals(0, admin.getMetrics().pendingReports());
        assertFalse(admin.dismissReport(501, "Stale decision").isSuccess());
        SpamReport report = newReport();
        assertTrue(admin.dismissReport(report.getId(), "Legitimate discussion").isSuccess());
        assertEquals(ReportStatus.DISMISSED, report.getStatus());
        assertEquals(UserStatus.ACTIVE, admin.getUser(2).orElseThrow().getStatus());
    }

    @Test void warnRecordsDecisionWithoutSanctionWhileSuspendReviewsAndLocks() {
        SpamReport warning = newReport();
        assertTrue(admin.warnUser(warning.getId(), "Please follow the rules").isSuccess());
        assertEquals(ReportStatus.REVIEWED, warning.getStatus());
        assertTrue(warning.getReviewNotes().contains("Please follow the rules"));
        assertTrue(warning.getReviewNotes().toLowerCase().contains("warn"));
        assertEquals(UserStatus.ACTIVE, admin.getUser(2).orElseThrow().getStatus());
        SpamReport suspension = newReport();
        var other = new InMemoryAdminService(store);
        var observed = new ArrayList<AdminService.DashboardMetrics>();
        other.metricsProperty().addListener((o, old, value) -> observed.add(value));
        assertTrue(admin.suspendUser(suspension.getId(), "Repeated abuse").isSuccess());
        assertEquals(ReportStatus.REVIEWED, suspension.getStatus());
        assertTrue(suspension.getReviewNotes().contains("Repeated abuse"));
        assertEquals(UserStatus.LOCKED, admin.getUser(2).orElseThrow().getStatus());
        assertEquals(3, admin.getMetrics().lockedUsers());
        assertEquals(1, other.getMetrics().pendingReports());
        assertFalse(observed.isEmpty());
    }

    @Test void invalidOrStaleModerationDecisionsDoNotChangeState() {
        for (String notes : new String[]{null, " "}) {
            assertFalse(admin.reviewReport(501, notes).isSuccess());
            assertFalse(admin.dismissReport(501, notes).isSuccess());
            assertFalse(admin.warnUser(501, notes).isSuccess());
            assertFalse(admin.suspendUser(501, notes).isSuccess());
        }
        for (long id : new long[]{502, 503, 999}) {
            assertFalse(admin.reviewReport(id, "Denied").isSuccess());
            assertFalse(admin.dismissReport(id, "Denied").isSuccess());
            assertFalse(admin.warnUser(id, "Denied").isSuccess());
            assertFalse(admin.suspendUser(id, "Denied").isSuccess());
        }
        assertEquals(1, admin.getMetrics().pendingReports());
        assertEquals(2, admin.getMetrics().lockedUsers());
    }

    @Test void lockUnlockMutatesSameAccountAndUpdatesMetricsAcrossInstances() {
        UserAccount user = admin.getUser(2).orElseThrow();
        assertEquals(24, admin.getMetrics().totalUsers());
        assertEquals(22, admin.getMetrics().activeUsers());
        assertTrue(admin.lockUser(2).isSuccess());
        assertSame(user, admin.getUser(2).orElseThrow());
        assertEquals(UserStatus.LOCKED, user.getStatus());
        assertEquals(21, admin.getMetrics().activeUsers());
        assertEquals(3, admin.getMetrics().lockedUsers());
        assertFalse(admin.lockUser(2).isSuccess());
        assertTrue(admin.unlockUser(2).isSuccess());
        assertEquals(22, admin.getMetrics().activeUsers());
        assertFalse(admin.unlockUser(2).isSuccess());
        assertFalse(admin.lockUser(999).isSuccess());
        assertFalse(admin.unlockUser(999).isSuccess());
        user.setStatus(UserStatus.DELETED);
        assertFalse(admin.unlockUser(2).isSuccess());
        assertFalse(admin.lockUser(2).isSuccess());
    }

    @Test void userSearchSortAndPaginationHaveStableOrderingAndStatusFilters() {
        assertEquals(List.of(1L), userIds(admin.searchUsers(" ALEX ", null, AdminService.UserSort.NAME, 0, 10)));
        assertEquals(List.of(18L, 3L), userIds(admin.searchUsers("", UserStatus.LOCKED, AdminService.UserSort.NAME, 0, 10)));
        assertEquals(List.of(24L, 23L, 22L), userIds(admin.searchUsers("", null, AdminService.UserSort.NEWEST, 0, 3)));
        assertEquals(List.of(21L, 20L, 19L), userIds(admin.searchUsers("", null, AdminService.UserSort.NEWEST, 1, 3)));
        assertEquals(List.of(1L, 2L, 3L), userIds(admin.searchUsers("", null, AdminService.UserSort.RECENT_LOGIN, 0, 3)));
        assertTrue(admin.searchUsers("missing", null, AdminService.UserSort.NAME, 0, 10).isEmpty());
        assertTrue(admin.searchUsers("", null, AdminService.UserSort.NAME, Integer.MAX_VALUE, 10).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> admin.searchUsers("", null, AdminService.UserSort.NAME, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> admin.searchUsers("", null, AdminService.UserSort.NAME, 0, 0));
    }

    @Test void groupAndReportFiltersSortsAndDetailsAreDeterministic() {
        assertEquals(List.of(20L, 21L, 22L), groupIds(admin.searchGroups("", AdminService.GroupSort.MEMBER_COUNT, 0, 10)));
        assertEquals(List.of(21L), groupIds(admin.searchGroups(" java ", AdminService.GroupSort.NAME, 0, 10)));
        assertEquals(List.of(20L, 21L, 22L), groupIds(admin.searchGroups("", AdminService.GroupSort.NEWEST, 0, 10)));
        assertEquals(List.of(501L), reportIds(admin.searchReports("promotional", ReportStatus.PENDING, ReportService.ReportSort.NEWEST, 0, 10)));
        assertEquals(List.of(503L, 502L, 501L), reportIds(admin.searchReports("", null, ReportService.ReportSort.NEWEST, 0, 10)));
        assertEquals(List.of(502L), reportIds(admin.searchReports("", null, ReportService.ReportSort.OLDEST, 1, 1)));
        assertTrue(admin.getGroup(10).isEmpty());
        assertTrue(admin.getGroup(999).isEmpty());
        assertTrue(admin.getReport(999).isEmpty());
        assertEquals(4, admin.getGroupMembers(20).size());
        assertTrue(admin.getGroupMembers(10).isEmpty());
        assertEquals(6, admin.getLoginHistory(1).size());
        assertEquals(10, admin.getUserActivity(1).size());
        assertEquals(3, admin.getGroupActivity(20).size());
        assertTrue(admin.getUserActivity(999).isEmpty());
        assertThrows(UnsupportedOperationException.class, admin.getUsers()::clear);
        assertThrows(UnsupportedOperationException.class, admin.getGroups()::clear);
        assertThrows(UnsupportedOperationException.class, admin.getReports()::clear);
    }

    @Test void metricsAndTrendDeriveFromLiveStateAndInclusiveExclusiveDateRange() {
        assertEquals(3, admin.getMetrics().totalGroups());
        assertEquals(12, admin.getMetrics().messageCount());
        assertEquals(35, admin.getMetrics().loginCount());
        assertEquals(6, admin.getMetrics().activeUsersInRange());
        var trend = admin.getActivityTrend();
        assertThrows(UnsupportedOperationException.class, trend::clear);
        Instant from = Instant.parse("2026-11-12T00:00:00Z");
        Instant until = Instant.parse("2026-11-13T00:00:00Z");
        assertTrue(admin.setDateRange(from, until).isSuccess());
        assertEquals(12, admin.getMetrics().messageCount());
        assertEquals(1, admin.getMetrics().loginCount());
        assertEquals(5, admin.getMetrics().activeUsersInRange());
        assertEquals(1, trend.size());
        assertEquals("2026-11-12", trend.get(0).date().toString());
        assertEquals(1, trend.get(0).appOpens());
        assertEquals(12, trend.get(0).messages());
        assertFalse(admin.setDateRange(until, from).isSuccess());
        assertFalse(admin.setDateRange(from, from).isSuccess());
        assertFalse(admin.setDateRange(null, until).isSuccess());
        assertEquals(12, admin.getMetrics().messageCount());
        assertTrue(admin.setDateRange(MockFixtures.REFERENCE_TIME, MockFixtures.REFERENCE_TIME.plusSeconds(1)).isSuccess());
        assertEquals(1, admin.getMetrics().loginCount());
        assertEquals(0, admin.getMetrics().messageCount());
        var auth = new InMemoryAuthService(store);
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        new InMemoryGroupService(store, auth.getSession()).createGroup("New group", "");
        assertEquals(4, admin.getMetrics().totalGroups());
    }

    @Test void equalNamesCountsAndMissingLoginDatesUseIdTiesAcrossPages() {
        admin.getUser(2).orElseThrow().updateProfile("Same name", "miatran", "");
        admin.getUser(4).orElseThrow().updateProfile("Same name", "noahwilson", "");
        assertEquals(List.of(2L), userIds(admin.searchUsers("same name", null, AdminService.UserSort.NAME, 0, 1)));
        assertEquals(List.of(4L), userIds(admin.searchUsers("same name", null, AdminService.UserSort.NAME, 1, 1)));
        assertEquals(List.of(7L, 8L, 9L), userIds(admin.searchUsers("", null, AdminService.UserSort.RECENT_LOGIN, 2, 3)));
        var auth = new InMemoryAuthService(store);
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        var groups = new InMemoryGroupService(store, auth.getSession());
        groups.createGroup("Tie", "");
        groups.createGroup("Tie", "");
        assertEquals(List.of(23L), groupIds(admin.searchGroups("Tie", AdminService.GroupSort.MEMBER_COUNT, 0, 1)));
        assertEquals(List.of(24L), groupIds(admin.searchGroups("Tie", AdminService.GroupSort.MEMBER_COUNT, 1, 1)));
    }

    @Test void reportSearchFindsCaseIdAndReportedUserAsRequestedByModerationScreen() {
        assertEquals(List.of(501L), reportIds(admin.searchReports("501", null, ReportService.ReportSort.NEWEST, 0, 10)));
        assertEquals(List.of(501L), reportIds(admin.searchReports(" JORDAN ", null, ReportService.ReportSort.NEWEST, 0, 10)));
        assertEquals(List.of(503L, 502L), reportIds(admin.searchReports("mia.tran@example.com", null, ReportService.ReportSort.NEWEST, 0, 10)));
    }

    @Test void sendsAndLoginsUpdateMetricsAndTrendWhileRangeEndIsExclusive() {
        Instant reference = MockFixtures.REFERENCE_TIME;
        assertTrue(admin.setDateRange(reference.minusSeconds(1), reference).isSuccess());
        assertEquals(0, admin.getMetrics().loginCount());
        assertTrue(admin.getActivityTrend().isEmpty());
        assertTrue(admin.setDateRange(Instant.MIN, Instant.MAX).isSuccess());
        var auth = new InMemoryAuthService(store);
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        assertEquals(36, admin.getMetrics().loginCount());
        new InMemoryChatService(store, auth.getSession()).sendMessage(10, "Current activity");
        assertEquals(13, admin.getMetrics().messageCount());
        assertEquals(13, admin.getActivityTrend().stream().mapToLong(AdminService.DailyActivity::messages).sum());
        SpamReport report = newReport();
        admin.getUser(2).orElseThrow().setStatus(UserStatus.DELETED);
        assertFalse(admin.suspendUser(report.getId(), "Unavailable account").isSuccess());
        assertEquals(ReportStatus.PENDING, report.getStatus());
        assertEquals("", report.getReviewNotes());
    }

    @Test void loginWithoutSendingUpdatesEngagementAndUserActivityInFreshRange() {
        Instant start = Instant.now();
        assertTrue(admin.setDateRange(start, Instant.MAX).isSuccess());
        var trend = admin.getActivityTrend();
        assertEquals(0, admin.getMetrics().loginCount());
        assertEquals(0, admin.getMetrics().activeUsersInRange());
        assertTrue(trend.isEmpty());
        var auth = new InMemoryAuthService(store);
        assertTrue(auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false).isSuccess());
        assertEquals(1, admin.getMetrics().loginCount());
        assertEquals(1, admin.getMetrics().activeUsersInRange());
        assertEquals(0, admin.getMetrics().messageCount());
        assertEquals(1, trend.stream().mapToLong(AdminService.DailyActivity::appOpens).sum());
        assertEquals(0, trend.stream().mapToLong(AdminService.DailyActivity::messages).sum());
        var freshActivity = admin.getUserActivity(1).stream().filter(e -> !e.occurredAt().isBefore(start)).toList();
        assertEquals(1, freshActivity.size());
        assertEquals(ActivityType.APP_OPEN, freshActivity.get(0).type());
        assertEquals(admin.getLoginHistory(1).get(0).loggedInAt(), freshActivity.get(0).occurredAt());
        assertEquals(12, store.getMessages().size());
    }

    private SpamReport newReport() {
        var auth = new InMemoryAuthService(store);
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        return new InMemoryReportService(store, auth.getSession()).submitReport(1001, 2, "Spam").value().orElseThrow();
    }
    private static List<Long> userIds(List<UserAccount> list) { return list.stream().map(UserAccount::getId).toList(); }
    private static List<Long> groupIds(List<Conversation> list) { return list.stream().map(Conversation::getId).toList(); }
    private static List<Long> reportIds(List<SpamReport> list) { return list.stream().map(SpamReport::getId).toList(); }
}
