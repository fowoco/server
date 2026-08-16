package com.fowoco.server.notification.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Notification categories a user can opt in/out of. Keys and default states mirror
 * fowoco/client's ProfilePage notification list, kept in one place so both sides agree on
 * what "default on/off" and "required" mean.
 */
public enum NotificationPreferenceKey {
    SECURITY_PERMISSION("security-permission", true, true),
    APPROVAL_REQUEST("approval-request", true, false),
    DOCUMENT_SUBMITTED("document-submitted", true, false),
    DOCUMENT_NEEDS_FIX("document-needs-fix", true, false),
    DUE_SOON("due-soon", true, false),
    ASSIGNED("assigned", false, false),
    AGENT_READY("agent-ready", true, false);

    private final String key;
    private final boolean defaultEnabled;
    private final boolean required;

    NotificationPreferenceKey(String key, boolean defaultEnabled, boolean required) {
        this.key = key;
        this.defaultEnabled = defaultEnabled;
        this.required = required;
    }

    public String key() {
        return key;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    /** Required preferences (security/permission alerts) can never be disabled. */
    public boolean required() {
        return required;
    }

    public static NotificationPreferenceKey fromKey(String key) {
        for (NotificationPreferenceKey value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }

    public static Map<NotificationPreferenceKey, Boolean> defaults() {
        Map<NotificationPreferenceKey, Boolean> defaults = new LinkedHashMap<>();
        for (NotificationPreferenceKey value : values()) {
            defaults.put(value, value.defaultEnabled);
        }
        return defaults;
    }
}
