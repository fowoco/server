package com.fowoco.server.auth.infrastructure.web;

/**
 * Best-effort "Browser · OS" label from a raw User-Agent header, for the login-history display
 * on the profile page. Not a security control — only used for a human-readable device hint.
 */
public final class UserAgentDeviceSummarizer {

    private static final String UNKNOWN = "알 수 없는 기기";

    private UserAgentDeviceSummarizer() {
    }

    public static String summarize(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String browser = browserOf(userAgent);
        String os = osOf(userAgent);
        if (browser == null && os == null) {
            return UNKNOWN;
        }
        if (browser == null) {
            return os;
        }
        if (os == null) {
            return browser;
        }
        return browser + " · " + os;
    }

    private static String browserOf(String userAgent) {
        // Order matters: Edge/Chrome/Samsung UAs also contain "Safari", and Chrome-based
        // Edge/Opera UAs also contain "Chrome".
        if (userAgent.contains("Edg/") || userAgent.contains("EdgA/") || userAgent.contains("EdgiOS/")) {
            return "Edge";
        }
        if (userAgent.contains("OPR/") || userAgent.contains("Opera")) {
            return "Opera";
        }
        if (userAgent.contains("SamsungBrowser/")) {
            return "Samsung Internet";
        }
        if (userAgent.contains("Firefox/") && !userAgent.contains("Seamonkey/")) {
            return "Firefox";
        }
        if (userAgent.contains("Chrome/") || userAgent.contains("CriOS/")) {
            return "Chrome";
        }
        if (userAgent.contains("Safari/") && (userAgent.contains("Version/") || userAgent.contains("Mobile/"))) {
            return "Safari";
        }
        return null;
    }

    private static String osOf(String userAgent) {
        if (userAgent.contains("Windows")) {
            return "Windows";
        }
        if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iPod")) {
            return "iOS";
        }
        if (userAgent.contains("Mac OS X") || userAgent.contains("Macintosh")) {
            return "macOS";
        }
        if (userAgent.contains("Android")) {
            return "Android";
        }
        if (userAgent.contains("Linux")) {
            return "Linux";
        }
        return null;
    }
}
