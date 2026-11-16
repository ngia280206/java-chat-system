package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.mock.*;
import com.chatsystem.shared.model.*;
import com.chatsystem.shared.model.status.*;
import com.chatsystem.shared.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryReportServiceTest {
    private MockDataStore store;
    private InMemoryAuthService auth;
    private ReportService reports;

    @BeforeEach void setUp() {
        store = MockFixtures.createStore();
        auth = new InMemoryAuthService(store);
        auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        reports = new InMemoryReportService(store, auth.getSession());
    }

    @Test void submitsVisibleMessageWithActualSenderAndRefreshesHeldHistory() {
        var held = reports.getReports();
        var other = new InMemoryReportService(store, auth.getSession()).getReports();
        SpamReport report = reports.submitReport(1001, 2, " Repeated spam ").value().orElseThrow();
        assertEquals(1, report.getReporterId());
        assertEquals(2, report.getReportedUserId());
        assertEquals(1001, report.getMessageId());
        assertEquals("Repeated spam", report.getReason());
        assertEquals(ReportStatus.PENDING, report.getStatus());
        assertTrue(held.contains(report));
        assertTrue(other.contains(report));
        assertEquals(4, store.getReports().size());
        assertThrows(UnsupportedOperationException.class, held::clear);
    }

    @Test void rejectsWrongSenderSelfInaccessibleHiddenClearedAndInvalidTargets() {
        assertFalse(reports.submitReport(1001, 4, "Reason").isSuccess());
        assertFalse(reports.submitReport(1002, 1, "Reason").isSuccess());
        assertFalse(reports.submitReport(2101, 2, "Reason").isSuccess());
        assertFalse(reports.submitReport(9999, 2, "Reason").isSuccess());
        assertFalse(reports.submitReport(1001, 2, " ").isSuccess());
        assertFalse(reports.submitReport(1001, 2, null).isSuccess());
        var chat = new InMemoryChatService(store, auth.getSession());
        chat.hideMessage(1001);
        assertFalse(reports.submitReport(1001, 2, "Hidden").isSuccess());
        chat.clearHistory(10);
        assertFalse(reports.submitReport(1003, 2, "Cleared").isSuccess());
        assertEquals(3, store.getReports().size());
        // A locked sender is still a valid visible abuse target.
        assertTrue(reports.submitReport(1101, 3, "More spam").isSuccess());
    }

    @Test void historySearchFiltersSortsAndPaginatesOnlyCurrentReportersRecords() {
        reports.submitReport(1003, 2, "Another invitation");
        assertEquals(List.of(504L, 502L), ids(reports.searchReports("invitation", null, ReportService.ReportSort.NEWEST, 0, 10)));
        assertEquals(List.of(501L), ids(reports.searchReports("", ReportStatus.PENDING, ReportService.ReportSort.OLDEST, 0, 1)));
        assertEquals(List.of(504L), ids(reports.searchReports("", ReportStatus.PENDING, ReportService.ReportSort.OLDEST, 1, 1)));
        assertTrue(reports.searchReports("", null, ReportService.ReportSort.NEWEST, 99, 10).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> reports.searchReports("", null, ReportService.ReportSort.NEWEST, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> reports.searchReports("", null, ReportService.ReportSort.NEWEST, 0, 0));
        var held = reports.getReports();
        auth.logout();
        assertTrue(held.isEmpty());
        assertFalse(reports.submitReport(1001, 2, "Denied").isSuccess());
        auth.login("noah.wilson@example.com", MockFixtures.DEMO_PASSWORD, false);
        assertEquals(List.of(503L), ids(held));
    }

    @Test void lockedAndMismatchedSessionCannotReadOrSubmitReports() {
        store.getUsers().get(0).setStatus(UserStatus.LOCKED);
        assertTrue(reports.getReports().isEmpty());
        assertFalse(reports.submitReport(1001, 2, "Denied").isSuccess());
        store.getUsers().get(0).setStatus(UserStatus.ACTIVE);
        var mismatched = new InMemoryReportService(MockFixtures.createStore(), auth.getSession());
        assertTrue(mismatched.getReports().isEmpty());
        assertFalse(mismatched.submitReport(1001, 2, "Denied").isSuccess());
    }

    @Test void adminDecisionUpdatesHeldHistoryAndPendingSearchWithoutRemovingEvidence() {
        var held = reports.getReports();
        SpamReport report = held.stream().filter(r -> r.getId() == 501).findFirst().orElseThrow();
        assertEquals(1, reports.searchReports("", ReportStatus.PENDING, ReportService.ReportSort.NEWEST, 0, 10).size());
        assertTrue(new InMemoryAdminService(store).dismissReport(501, "Context is legitimate").isSuccess());
        assertSame(report, held.stream().filter(r -> r.getId() == 501).findFirst().orElseThrow());
        assertEquals(ReportStatus.DISMISSED, report.getStatus());
        assertTrue(reports.searchReports("", ReportStatus.PENDING, ReportService.ReportSort.NEWEST, 0, 10).isEmpty());
        assertEquals(List.of(501L), ids(reports.searchReports("LEGITIMATE", ReportStatus.DISMISSED, ReportService.ReportSort.NEWEST, 0, 10)));
    }

    @Test void reportSearchFindsCaseAndReportedUserWithoutExposingOtherReportersCases() {
        assertEquals(List.of(502L), ids(reports.searchReports(" MIA ", null, ReportService.ReportSort.NEWEST, 0, 10)));
        assertEquals(List.of(501L), ids(reports.searchReports("501", null, ReportService.ReportSort.NEWEST, 0, 10)));
        assertTrue(reports.searchReports("503", null, ReportService.ReportSort.NEWEST, 0, 10).isEmpty());
    }

    private static List<Long> ids(List<SpamReport> list) { return list.stream().map(SpamReport::getId).toList(); }
}
