package com.chatsystem.admin;

import com.chatsystem.shared.mock.*;
import com.chatsystem.shared.mock.service.*;
import com.chatsystem.shared.service.*;
import com.chatsystem.shared.session.UserSession;

/** One administrator application's independent process-local state graph. */
public final class AdminApplicationContext {
    private final MockDataStore store = MockFixtures.createStore();
    private final AuthService auth = new InMemoryAuthService(store);
    private final UserSession session = authenticate();
    private final AdminService admin = new InMemoryAdminService(store);
    private final GroupService groups = new InMemoryGroupService(store, session);
    private final ReportService reports = new InMemoryReportService(store, session);
    private UserSession authenticate() {
        var result = auth.login(MockFixtures.DEMO_EMAIL, MockFixtures.DEMO_PASSWORD, false);
        if (!result.isSuccess()) throw new IllegalStateException("Could not initialize admin demo state");
        return auth.getSession();
    }
    public MockDataStore store() { return store; }
    public AdminService admin() { return admin; }
    public GroupService groups() { return groups; }
    public ReportService reports() { return reports; }
}
