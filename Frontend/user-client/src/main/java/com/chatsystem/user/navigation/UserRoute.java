package com.chatsystem.user.navigation;

import static com.chatsystem.user.navigation.UserRoute.WindowProfile.*;

/** Typed destinations; screen workflows are supplied by their later tasks. */
public enum UserRoute {
    LOGIN("Welcome back", AUTH),
    REGISTER("Create an account", AUTH),
    FORGOT_PASSWORD("Forgot password", AUTH),
    VERIFICATION_CODE("Verification code", AUTH),
    RESET_PASSWORD("Reset password", AUTH),
    CHAT_HOME("Your conversations, all in one place", SHELL),
    CHAT_PRIVATE("Conversation", SHELL, ViewRequest.ConversationId.class),
    CHAT_GROUP("Group conversation", SHELL, ViewRequest.ConversationId.class),
    MESSAGE_SEARCH("Search messages", SHELL, ViewRequest.ConversationId.class),
    FRIENDS("Friends", SHELL),
    FRIEND_REQUESTS("Friend requests", SHELL),
    BLOCKED_USERS("Blocked users", SHELL),
    GROUP_MANAGEMENT("Groups", SHELL),
    GROUP_DETAILS("Group details", SHELL, ViewRequest.ConversationId.class),
    GROUP_INVITATIONS("Group invitations", SHELL),
    PROFILE("My profile", SHELL),
    SETTINGS("Settings", SHELL),
    CHANGE_PASSWORD("Change password", SHELL),
    SPAM_REPORTS("Spam reports", SHELL),
    REPORT_DETAIL("Report details", SHELL, ViewRequest.ReportId.class),
    AI_ASSISTANT("AI assistant", ASSISTANT, ViewRequest.ConversationId.class);

    public enum WindowProfile {
        AUTH(1240, 780), SHELL(1540, 930), ASSISTANT(890, 890);

        private final int width;
        private final int height;

        WindowProfile(int width, int height) { this.width = width; this.height = height; }
        public int width() { return width; }
        public int height() { return height; }
    }

    private final String title;
    private final WindowProfile profile;
    private final Class<? extends ViewRequest.EntityId> entityType;

    UserRoute(String title, WindowProfile profile) { this(title, profile, null); }

    UserRoute(String title, WindowProfile profile, Class<? extends ViewRequest.EntityId> entityType) {
        this.title = title;
        this.profile = profile;
        this.entityType = entityType;
    }

    public String title() { return title; }
    public WindowProfile profile() { return profile; }
    Class<? extends ViewRequest.EntityId> entityType() { return entityType; }

    public UserRoute sidebarRoute() {
        return switch (this) {
            case CHAT_PRIVATE, CHAT_GROUP, MESSAGE_SEARCH, AI_ASSISTANT -> CHAT_HOME;
            case FRIEND_REQUESTS, BLOCKED_USERS -> FRIENDS;
            case GROUP_DETAILS, GROUP_INVITATIONS -> GROUP_MANAGEMENT;
            case REPORT_DETAIL -> SPAM_REPORTS;
            case CHANGE_PASSWORD -> SETTINGS;
            default -> this;
        };
    }
}
