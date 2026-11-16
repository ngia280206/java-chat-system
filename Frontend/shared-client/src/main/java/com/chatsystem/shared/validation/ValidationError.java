package com.chatsystem.shared.validation;

import java.util.Objects;

/** A null field denotes feedback for the whole operation. */
public record ValidationError(String field, String message) {
    public ValidationError {
        Objects.requireNonNull(message);
        if (message.isBlank()) throw new IllegalArgumentException("An error needs a message");
        if (field != null && field.isBlank()) throw new IllegalArgumentException("A field name cannot be blank");
    }

    public static ValidationError field(String field, String message) {
        return new ValidationError(Objects.requireNonNull(field), message);
    }

    public static ValidationError global(String message) { return new ValidationError(null, message); }
    public boolean isGlobal() { return field == null; }
}
