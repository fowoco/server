package com.fowoco.server.workerimport.application;

import java.util.List;
import java.util.Map;

public record ParsedWorkerImport(List<String> headers, List<Map<String, String>> rows) {
    public ParsedWorkerImport {
        headers = List.copyOf(headers);
        rows = rows.stream().map(Map::copyOf).toList();
    }
}
