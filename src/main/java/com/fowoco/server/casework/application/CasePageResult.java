package com.fowoco.server.casework.application;

import java.util.List;

public record CasePageResult(
        List<CaseProjection> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public CasePageResult(List<CaseProjection> items, int page, int size, long totalElements) {
        this(
                List.copyOf(items),
                page,
                size,
                totalElements,
                totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size)
        );
    }
}
