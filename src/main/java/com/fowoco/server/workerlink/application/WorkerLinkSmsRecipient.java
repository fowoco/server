package com.fowoco.server.workerlink.application;

final class WorkerLinkSmsRecipient {

    private WorkerLinkSmsRecipient() {
    }

    static String normalizeKoreanMobile(String rawValue) {
        String digits = rawValue.replaceAll("[^0-9]", "");
        if (digits.startsWith("82") && digits.length() == 12) {
            digits = "0" + digits.substring(2);
        }
        if (!digits.matches("010[0-9]{8}")) {
            throw new IllegalArgumentException("recipientPhone must be a Korean mobile number");
        }
        return digits;
    }
}
