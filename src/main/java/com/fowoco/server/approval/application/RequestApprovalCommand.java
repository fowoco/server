package com.fowoco.server.approval.application;

import java.util.List;
import java.util.Map;

public record RequestApprovalCommand(
        long expectedVersion,
        Map<String, Object> aiSnapshot,
        Map<String, Object> hrSnapshot,
        List<String> changedFields,
        Map<String, Object> sourceVersions
) {
}
