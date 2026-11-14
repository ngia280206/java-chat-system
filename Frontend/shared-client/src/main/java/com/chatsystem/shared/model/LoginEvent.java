package com.chatsystem.shared.model;

import java.time.Instant;
import java.util.Objects;

/** Successful login history, enriched with local demo display context. */
public record LoginEvent(long id, long userId, Instant loggedInAt, String device, String location) {
    public LoginEvent {
        Objects.requireNonNull(loggedInAt);
        Objects.requireNonNull(device);
        Objects.requireNonNull(location);
    }
}
