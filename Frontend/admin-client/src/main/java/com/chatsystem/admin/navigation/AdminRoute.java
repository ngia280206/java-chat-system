package com.chatsystem.admin.navigation;

public enum AdminRoute {
    DASHBOARD("Overview"), USERS("People"), USER_DETAILS("User details", ViewRequest.UserId.class),
    GROUPS("Groups"), GROUP_DETAILS("Group details", ViewRequest.GroupId.class),
    MODERATION("Warnings"), WARNING_DETAILS("Warning details", ViewRequest.ReportId.class);
    private final String title; private final Class<? extends ViewRequest.EntityId> entityType;
    AdminRoute(String title) { this(title, null); }
    AdminRoute(String title, Class<? extends ViewRequest.EntityId> entityType) { this.title=title; this.entityType=entityType; }
    public String title() { return title; } Class<? extends ViewRequest.EntityId> entityType(){ return entityType; }
    public AdminRoute sidebarRoute() { return switch(this) { case USER_DETAILS -> USERS; case GROUP_DETAILS -> GROUPS; case WARNING_DETAILS -> MODERATION; default -> this; }; }
}
