package com.fowoco.server.settings.api;

import com.fowoco.server.settings.application.CompanyMemberView;
import com.fowoco.server.settings.application.DetailedCompanyMemberView;
import com.fowoco.server.settings.application.MinimalCompanyMemberView;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(name = "CompanyMemberListResponse", description = "같은 사업장의 구성원 목록")
public record CompanyMemberListResponse(
        @ArraySchema(
                schema = @Schema(implementation = CompanyMemberItemResponse.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        )
        @NotNull
        List<CompanyMemberItemResponse> items
) {

    public CompanyMemberListResponse {
        items = List.copyOf(items);
    }

    static CompanyMemberListResponse from(List<CompanyMemberView> members) {
        return new CompanyMemberListResponse(members.stream()
                .map(CompanyMemberListResponse::item)
                .toList());
    }

    private static CompanyMemberItemResponse item(CompanyMemberView member) {
        if (member instanceof DetailedCompanyMemberView detailed) {
            return new DetailedCompanyMemberResponse(
                    detailed.userId(),
                    detailed.displayName(),
                    detailed.roles(),
                    detailed.active(),
                    detailed.approvalPermission()
            );
        }
        MinimalCompanyMemberView minimal = (MinimalCompanyMemberView) member;
        return new MinimalCompanyMemberResponse(minimal.userId(), minimal.displayName());
    }
}
