package com.fowoco.server.settings.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.settings.domain.ApprovalPolicy;
import com.fowoco.server.settings.domain.AuditVisibility;
import com.fowoco.server.settings.domain.CompanySettings;
import com.fowoco.server.task.domain.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Set;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(
        name = "CompanySettingsResponse",
        description = "Secret과 개인정보를 제외한 사업장 공통 운영 정책"
)
public record CompanySettingsResponse(
        @Schema(
                description = "Task 승인·반려 명령을 수행할 수 있는 역할 범위",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "ADMIN_OR_HR"
        )
        ApprovalPolicy approvalPolicy,
        @Schema(
                description = "근로자 보안 링크 기본 만료시간. Server MVP 범위는 1~168시간",
                minimum = "1",
                maximum = "168",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "72"
        )
        long linkExpiryHours,
        @Schema(
                description = "TaskType별 회사 추가 필수 EvidenceType. 기존 필수 증빙 조건을 완화하지 않음",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "{\"RECONTRACT\":[\"DOCUMENT\"]}"
        )
        Map<TaskType, Set<EvidenceType>> evidenceRules,
        @Schema(
                description = "파일 보유기간(일). 정책 저장·조회만 제공",
                minimum = "30",
                maximum = "3650",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "365"
        )
        int fileRetentionDays,
        @Schema(
                description = "ai_attempt 품질·실행 상세 데이터 보유기간(일). ai_run aggregate는 제외",
                minimum = "7",
                maximum = "365",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "90"
        )
        int aiLogRetentionDays,
        @Schema(
                description = "company-wide 감사로그 검색을 허용할 역할 범위",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "ADMIN_ONLY"
        )
        AuditVisibility auditVisibility,
        @Schema(
                description = "회사 설정 aggregate version",
                minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "0"
        )
        long version
) {

    static CompanySettingsResponse from(CompanySettings settings) {
        return new CompanySettingsResponse(
                settings.approvalPolicy(),
                settings.linkExpiryHours(),
                settings.evidenceRules(),
                settings.fileRetentionDays(),
                settings.aiLogRetentionDays(),
                settings.auditVisibility(),
                settings.version()
        );
    }
}
