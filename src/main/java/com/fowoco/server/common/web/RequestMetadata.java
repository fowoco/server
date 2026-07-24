package com.fowoco.server.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record RequestMetadata(String requestId, String traceId) {

    private static final Pattern TRACEPARENT = Pattern.compile(
            "^[\\da-f]{2}-([\\da-f]{32})-[\\da-f]{16}-[\\da-f]{2}$"
    );

    public static RequestMetadata from(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        String traceId = parseTraceId(request.getHeader("traceparent"));
        return new RequestMetadata(requestId == null ? "unknown" : requestId.toString(), traceId);
    }

    private static String parseTraceId(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        Matcher matcher = TRACEPARENT.matcher(traceparent.toLowerCase());
        return matcher.matches() ? matcher.group(1) : null;
    }
}
