package com.fowoco.server.workerimport.domain;

import java.util.Arrays;

public enum WorkerImportField {
    DISPLAY_NAME("display_name"),
    NATIONALITY_CODE("nationality_code"),
    PREFERRED_LANGUAGE("preferred_language"),
    VISA_TYPE("visa_type"),
    STAY_EXPIRY_DATE("stay_expiry_date"),
    CONTRACT_START_DATE("contract_start_date"),
    CONTRACT_END_DATE("contract_end_date"),
    EMPLOYMENT_PERMIT_END_DATE("employment_permit_end_date"),
    EMPLOYMENT_ACTIVITY_END_DATE("employment_activity_end_date");

    private final String key;

    WorkerImportField(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static WorkerImportField fromKey(String key) {
        return Arrays.stream(values())
                .filter(field -> field.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported worker import field: " + key));
    }
}
