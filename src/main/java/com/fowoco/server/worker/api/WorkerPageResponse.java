package com.fowoco.server.worker.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "WorkerPageResponse", description = "근로자 목록 페이지 응답")
public final class WorkerPageResponse {

    @JsonProperty("items")
    @Schema(description = "이번 페이지의 근로자 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<WorkerResponse> items;

    @JsonProperty("page")
    @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int page;

    @JsonProperty("size")
    @Schema(description = "페이지당 항목 수", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int size;

    @JsonProperty("total_elements")
    @Schema(name = "total_elements", description = "전체 근로자 수", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private final long totalElements;

    public WorkerPageResponse(List<WorkerResponse> items, int page, int size, long totalElements) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
    }

    public List<WorkerResponse> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }
}
