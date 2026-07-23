package com.example.sharepointdocusign.util;

public final class LogMaskingUtil {

    private LogMaskingUtil() {
    }

    /**
     * Masks the local part of an email address for INFO-level logging,
     * e.g. "j***n@example.com" for "john@example.com".
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String maskedLocal = (local.length() <= 2)
                ? local.charAt(0) + "*"
                : local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1);
        return maskedLocal + "@" + domain;
    }
}
