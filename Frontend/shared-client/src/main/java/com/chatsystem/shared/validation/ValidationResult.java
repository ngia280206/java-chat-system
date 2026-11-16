package com.chatsystem.shared.validation;

import java.util.List;
import java.util.Optional;

/** Success may have a null value for a void operation; failures always carry errors. */
public final class ValidationResult<T> {
    private final T value;
    private final List<ValidationError> errors;

    private ValidationResult(T value, List<ValidationError> errors) {
        this.value = value;
        this.errors = List.copyOf(errors);
    }

    public static <T> ValidationResult<T> success(T value) {
        return new ValidationResult<>(value, List.of());
    }

    public static <T> ValidationResult<T> failure(List<ValidationError> errors) {
        if (errors.isEmpty()) throw new IllegalArgumentException("A failure needs at least one error");
        return new ValidationResult<>(null, errors);
    }

    public boolean isSuccess() { return errors.isEmpty(); }
    public Optional<T> value() { return Optional.ofNullable(value); }
    public List<ValidationError> errors() { return errors; }
}
