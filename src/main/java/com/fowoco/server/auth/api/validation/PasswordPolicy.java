package com.fowoco.server.auth.api.validation;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;
    public static final int MAX_UTF8_BYTES = 72;
    public static final String LETTER_AND_DIGIT_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";

    private PasswordPolicy() {
    }
}
