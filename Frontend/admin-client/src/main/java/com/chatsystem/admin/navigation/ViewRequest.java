package com.chatsystem.admin.navigation;

import java.util.*;

public record ViewRequest(AdminRoute route, Optional<EntityId> entityId) {
    public sealed interface EntityId permits UserId, GroupId, ReportId { long value(); }
    public record UserId(long value) implements EntityId { public UserId { if(value<=0) throw new IllegalArgumentException("User ID must be positive"); } }
    public record GroupId(long value) implements EntityId { public GroupId { if(value<=0) throw new IllegalArgumentException("Group ID must be positive"); } }
    public record ReportId(long value) implements EntityId { public ReportId { if(value<=0) throw new IllegalArgumentException("Report ID must be positive"); } }
    public ViewRequest { Objects.requireNonNull(route); Objects.requireNonNull(entityId); if(route.entityType()==null ? entityId.isPresent() : entityId.isEmpty() || !route.entityType().isInstance(entityId.get())) throw new IllegalArgumentException("Invalid entity ID for route "+route); }
    public static ViewRequest of(AdminRoute route){ return new ViewRequest(route, Optional.empty()); }
    public static ViewRequest user(long id){ return new ViewRequest(AdminRoute.USER_DETAILS, Optional.of(new UserId(id))); }
    public static ViewRequest group(long id){ return new ViewRequest(AdminRoute.GROUP_DETAILS, Optional.of(new GroupId(id))); }
    public static ViewRequest report(long id){ return new ViewRequest(AdminRoute.WARNING_DETAILS, Optional.of(new ReportId(id))); }
}
