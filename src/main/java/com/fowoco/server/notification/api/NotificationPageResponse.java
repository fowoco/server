package com.fowoco.server.notification.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "NotificationPageResponse", description = "알림 목록 응답")
public final class NotificationPageResponse {

    @JsonProperty("items")
    private final List<NotificationItemResponse> items;

    @JsonProperty("unread_count")
    @Schema(name = "unread_count", description = "읽지 않은 알림 개수")
    private final long unreadCount;

    @JsonProperty("has_next")
    @Schema(name = "has_next", description = "다음 페이지 존재 여부")
    private final boolean hasNext;

    @JsonProperty("next_cursor")
    @Schema(name = "next_cursor", description = "다음 페이지 조회용 커서 (없으면 마지막 페이지)")
    private final String nextCursor;

    public NotificationPageResponse(
            List<NotificationItemResponse> items, long unreadCount, boolean hasNext, String nextCursor
    ) {
        this.items = items;
        this.unreadCount = unreadCount;
        this.hasNext = hasNext;
        this.nextCursor = nextCursor;
    }

    public List<NotificationItemResponse> getItems() {
        return items;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
