package com.fowoco.server.auth.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public final class Utf8ByteLengthValidator implements ConstraintValidator<Utf8ByteLength, String> {

    private int maximumBytes;

    @Override
    public void initialize(Utf8ByteLength annotation) {
        if (annotation.max() <= 0) {
            throw new IllegalArgumentException("Utf8ByteLength max must be positive");
        }
        maximumBytes = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= maximumBytes;
    }
}
