package com.fowoco.server.casework.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.casework.application.CasePageResult;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CasePageResponse(
        List<CaseSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    static CasePageResponse from(CasePageResult result) {
        return new CasePageResponse(
                result.items().stream().map(CaseSummaryResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }
}
