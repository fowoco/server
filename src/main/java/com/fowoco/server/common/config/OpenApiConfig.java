package com.fowoco.server.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String BEARER_AUTH_SCHEME = "bearerAuth";
    private static final String REFRESH_COOKIE_SCHEME = "refreshCookie";

    @Bean
    public OpenAPI fowocoOpenApi() {
        ObjectSchema fieldErrorSchema = new ObjectSchema();
        fieldErrorSchema.addProperty("field", new StringSchema().example("display_name"));
        fieldErrorSchema.addProperty("message", new StringSchema().example("값을 입력해 주세요."));

        ObjectSchema errorSchema = new ObjectSchema();
        errorSchema.addProperty("timestamp", new DateTimeSchema());
        errorSchema.addProperty("status", new IntegerSchema().example(400));
        errorSchema.addProperty("code", new StringSchema().example("VALIDATION_FAILED"));
        errorSchema.addProperty("message", new StringSchema().example("입력값을 확인해 주세요."));
        errorSchema.addProperty("path", new StringSchema().example("/api/v1/workers"));
        errorSchema.addProperty("request_id", new StringSchema().example("01-example-request-id"));
        errorSchema.addProperty("field_errors", new ArraySchema().items(fieldErrorSchema));

        Components components = new Components()
                .addSchemas("ApiErrorResponse", errorSchema)
                .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("로그인 응답의 Access Token을 Authorization: Bearer <access_token> 형식으로 전달합니다. "
                                + "JWT에는 sub(user_id), company_id, roles, token_type, iss, aud, exp, jti가 포함됩니다."))
                .addSecuritySchemes(REFRESH_COOKIE_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("fowoco_refresh_token")
                        .description("Refresh·Logout에서만 사용하는 HttpOnly 쿠키입니다. "
                                + "JSON 본문이나 Authorization 헤더로 전송하지 않습니다."))
                .addHeaders("RequestId", new Header()
                        .description("요청과 로그를 함께 찾기 위한 추적 ID")
                        .schema(new StringSchema()))
                .addHeaders("RefreshTokenCookie", new Header()
                        .description("Refresh Token을 전달하는 HttpOnly 쿠키입니다. 원문은 JSON 응답에 포함하지 않습니다. "
                                + "MVP는 same-site 배포에서 SameSite=Strict 또는 Lax만 허용하며, "
                                + "SameSite=None은 CSRF 또는 신뢰 Origin 검증을 갖추기 전에는 사용하지 않습니다.")
                        .schema(new StringSchema().example(
                                "fowoco_refresh_token=<opaque-token>; Path=/api/v1/auth; "
                                        + "Max-Age=1209600; HttpOnly; SameSite=Strict"
                        )))
                .addHeaders("ExpiredRefreshTokenCookie", new Header()
                        .description("브라우저에 저장된 Refresh Token을 삭제하는 만료 쿠키입니다. "
                                + "발급 쿠키와 같은 이름과 Path를 사용하고 Max-Age=0으로 만료시킵니다.")
                        .schema(new StringSchema().example(
                                "fowoco_refresh_token=; Path=/api/v1/auth; "
                                        + "Max-Age=0; HttpOnly; SameSite=Strict"
                        )))
                .addResponses("BadRequest", errorResponse("요청 형식 또는 입력값 오류"))
                .addResponses("Unauthorized", errorResponse("인증 필요"))
                .addResponses("InvalidCredentials", errorResponse(
                        "이메일·비밀번호·계정 또는 사업장 상태로 인한 로그인 실패",
                        Map.of(
                                "timestamp", "2026-07-22T01:00:00Z",
                                "status", 401,
                                "code", "INVALID_CREDENTIALS",
                                "message", "이메일 또는 비밀번호를 확인해 주세요.",
                                "path", "/api/v1/auth/login",
                                "request_id", "01-example-request-id",
                                "field_errors", java.util.List.of()
                        )
                ))
                .addResponses("InvalidRefreshToken", errorResponse(
                        "Refresh Token이 없거나 유효하지 않아 재발급할 수 없음",
                        Map.of(
                                "timestamp", "2026-07-22T01:10:00Z",
                                "status", 401,
                                "code", "INVALID_REFRESH_TOKEN",
                                "message", "로그인 정보를 갱신할 수 없습니다. 다시 로그인해 주세요.",
                                "path", "/api/v1/auth/refresh",
                                "request_id", "01-example-request-id",
                                "field_errors", java.util.List.of()
                        )
                ).addHeaderObject(
                        org.springframework.http.HttpHeaders.SET_COOKIE,
                        new Header().$ref("#/components/headers/ExpiredRefreshTokenCookie")
                ))
                .addResponses("Forbidden", errorResponse("권한 부족"))
                .addResponses("NotFound", errorResponse("리소스를 찾을 수 없음"))
                .addResponses("MethodNotAllowed", errorResponse("지원하지 않는 HTTP 메서드"))
                .addResponses("NotAcceptable", errorResponse("제공할 수 없는 응답 형식"))
                .addResponses("UnsupportedMediaType", errorResponse("지원하지 않는 요청 형식"))
                .addResponses("InternalServerError", errorResponse("서버 내부 오류"));

        return new OpenAPI()
                .info(new Info()
                        .title("FOWOCO Server API")
                        .version("0.1.0")
                        .description("E-9 외국인근로자 고용 사업장의 HR 업무카드와 승인 Workflow API"))
                .components(components);
    }

    @Bean
    public OpenApiCustomizer commonApiContractCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    operation.addParametersItem(new HeaderParameter()
                            .name(REQUEST_ID_HEADER)
                            .required(false)
                            .description("생략하면 서버가 생성하며 응답 헤더와 오류 본문에 반환합니다.")
                            .schema(new StringSchema()
                                    .maxLength(128)
                                    .pattern("^[A-Za-z0-9._:-]+$")));
                    operation.getResponses().values().stream()
                            .filter(response -> response.get$ref() == null)
                            .forEach(response -> response.addHeaderObject(
                                    REQUEST_ID_HEADER,
                                    new Header().$ref("#/components/headers/RequestId")
                            ));
                    operation.getResponses().putIfAbsent("405", responseReference("MethodNotAllowed"));
                    operation.getResponses().putIfAbsent("406", responseReference("NotAcceptable"));
                    operation.getResponses().putIfAbsent("500", responseReference("InternalServerError"));
                })
        );
    }

    private ApiResponse errorResponse(String description) {
        return errorResponse(description, null);
    }

    private ApiResponse errorResponse(String description, Object example) {
        MediaType mediaType = new MediaType()
                .schema(new ObjectSchema().$ref("#/components/schemas/ApiErrorResponse"));
        if (example != null) {
            mediaType.example(example);
        }
        return new ApiResponse()
                .description(description)
                .addHeaderObject(REQUEST_ID_HEADER, new Header().$ref("#/components/headers/RequestId"))
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        mediaType
                ));
    }

    private ApiResponse responseReference(String name) {
        return new ApiResponse().$ref("#/components/responses/" + name);
    }
}
