package com.fowoco.server.settings.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "CompanyMemberItemResponse",
        oneOf = {
                DetailedCompanyMemberResponse.class,
                MinimalCompanyMemberResponse.class
        }
)
public sealed interface CompanyMemberItemResponse
        permits DetailedCompanyMemberResponse, MinimalCompanyMemberResponse {
}
