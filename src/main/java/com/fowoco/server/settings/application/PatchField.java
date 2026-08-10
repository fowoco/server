package com.fowoco.server.settings.application;

import java.util.Objects;

public record PatchField<T>(boolean present, T value) {

    public PatchField {
        if (present) {
            Objects.requireNonNull(value, "present patch value must not be null");
        } else if (value != null) {
            throw new IllegalArgumentException("absent patch value must be null");
        }
    }

    public static <T> PatchField<T> absent() {
        return new PatchField<>(false, null);
    }

    public static <T> PatchField<T> of(T value) {
        return new PatchField<>(true, value);
    }

    public T orElse(T currentValue) {
        return present ? value : currentValue;
    }
}
