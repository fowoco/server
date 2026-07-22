package com.fowoco.server.auth.application;

import java.util.regex.Pattern;

public final class RefreshTokenFormat {

    public static final int ENTROPY_BYTES = 32;
    private static final int RAW_VALUE_LENGTH = 43;
    private static final Pattern URL_SAFE_BASE64_WITHOUT_PADDING =
            Pattern.compile("[A-Za-z0-9_-]{" + RAW_VALUE_LENGTH + "}");

    private RefreshTokenFormat() {
    }

    public static boolean isValidRawValue(String value) {
        return value != null && URL_SAFE_BASE64_WITHOUT_PADDING.matcher(value).matches();
    }
}
