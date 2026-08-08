package com.fowoco.server.workerimport.application;

import java.util.Map;

public record WorkerImportRowPatch(int rowNumber, Boolean excluded, Map<String, String> values) {
    public WorkerImportRowPatch {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
