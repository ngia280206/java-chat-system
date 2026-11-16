package com.chatsystem.user.navigation;

import java.util.Objects;
import java.util.Optional;

/** Detail destinations cannot be created with absent or unrelated identifiers. */
public record ViewRequest(UserRoute route, Optional<EntityId> entityId) {
    public sealed interface EntityId permits ConversationId, ReportId { long value(); }

    public record ConversationId(long value) implements EntityId {
        public ConversationId {
            if (value <= 0) throw new IllegalArgumentException("Conversation ID must be positive");
        }
    }

    public record ReportId(long value) implements EntityId {
        public ReportId {
            if (value <= 0) throw new IllegalArgumentException("Report ID must be positive");
        }
    }

    public ViewRequest {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(entityId, "entityId");
        if (route.entityType() == null ? entityId.isPresent()
                : entityId.isEmpty() || !route.entityType().isInstance(entityId.get())) {
            throw new IllegalArgumentException("Invalid entity ID for route " + route);
        }
    }

    public static ViewRequest of(UserRoute route) { return new ViewRequest(route, Optional.empty()); }

    public static ViewRequest conversation(UserRoute route, long conversationId) {
        return new ViewRequest(route, Optional.of(new ConversationId(conversationId)));
    }

    public static ViewRequest report(long reportId) {
        return new ViewRequest(UserRoute.REPORT_DETAIL, Optional.of(new ReportId(reportId)));
    }
}
