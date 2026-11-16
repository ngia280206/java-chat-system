package com.chatsystem.shared.mock.service;

import com.chatsystem.shared.model.SpamReport;
import com.chatsystem.shared.model.UserAccount;
import com.chatsystem.shared.model.status.ReportStatus;
import com.chatsystem.shared.service.ReportService.ReportSort;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Shared report history/queue rules and paging for local query snapshots. */
final class MockServiceQueries {
    private MockServiceQueries() { }

    static String normalize(String text) { return text == null ? "" : text.strip().toLowerCase(Locale.ROOT); }

    static <T> List<T> page(List<T> values, int page, int pageSize) {
        if (page < 0 || pageSize <= 0) throw new IllegalArgumentException("Use a nonnegative page and positive page size");
        long start = (long) page * pageSize;
        if (start >= values.size()) return List.of();
        return List.copyOf(values.subList((int) start, (int) Math.min(start + pageSize, values.size())));
    }

    static List<SpamReport> reports(List<SpamReport> reports, List<UserAccount> users, String query, ReportStatus status,
                                    ReportSort sort, int page, int pageSize) {
        String text = normalize(query);
        Comparator<SpamReport> order = Comparator.comparing(SpamReport::getCreatedAt).thenComparingLong(SpamReport::getId);
        if (Objects.requireNonNull(sort) == ReportSort.NEWEST) order = order.reversed();
        return page(reports.stream().filter(r -> status == null || r.getStatus() == status)
                .filter(r -> Long.toString(r.getId()).contains(text) || normalize(r.getReason()).contains(text)
                        || normalize(r.getReviewNotes()).contains(text) || users.stream().anyMatch(u -> u.getId() == r.getReportedUserId()
                        && (normalize(u.getDisplayName()).contains(text) || normalize(u.getUsername()).contains(text)
                        || normalize(u.getEmail()).contains(text))))
                .sorted(order).toList(), page, pageSize);
    }
}
