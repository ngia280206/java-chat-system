package com.chatsystem.user;

import com.chatsystem.shared.mock.MockDataStore;
import com.chatsystem.shared.mock.MockFixtures;
import com.chatsystem.shared.mock.service.*;
import com.chatsystem.shared.service.*;
import com.chatsystem.shared.session.UserSession;

/** One user application's independent, process-local service graph. */
public final class UserApplicationContext {
    private final MockDataStore store = MockFixtures.createStore();
    private final AuthService auth = new InMemoryAuthService(store);
    private final UserSession session = auth.getSession();
    private final ProfileService profiles = new InMemoryProfileService(store, session);
    private final SettingsService settings = new InMemorySettingsService(store, session);
    private final SocialService social = new InMemorySocialService(store, session);
    private final GroupService groups = new InMemoryGroupService(store, session);
    private final ChatService chat = new InMemoryChatService(store, session);
    private final ReportService reports = new InMemoryReportService(store, session);
    private final AdminService admin = new InMemoryAdminService(store);
    private final AssistantService assistant = new PredefinedAssistantService(chat);

    public MockDataStore store() { return store; }
    public AuthService auth() { return auth; }
    public UserSession session() { return session; }
    public ProfileService profiles() { return profiles; }
    public SettingsService settings() { return settings; }
    public SocialService social() { return social; }
    public GroupService groups() { return groups; }
    public ChatService chat() { return chat; }
    public ReportService reports() { return reports; }
    public AdminService admin() { return admin; }
    public AssistantService assistant() { return assistant; }
}
