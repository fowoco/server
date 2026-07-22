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
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

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
                .addHeaders("RequestId", new Header()
                        .description("요청과 로그를 함께 찾기 위한 추적 ID")
                        .schema(new StringSchema()))
                .addResponses("BadRequest", errorResponse("요청 형식 또는 입력값 오류"))
                .addResponses("Unauthorized", errorResponse("인증 필요"))
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
                    operation.getResponses().putIfAbsent("400", responseReference("BadRequest"));
                    operation.getResponses().putIfAbsent("401", responseReference("Unauthorized"));
                    operation.getResponses().putIfAbsent("403", responseReference("Forbidden"));
                    operation.getResponses().putIfAbsent("404", responseReference("NotFound"));
                    operation.getResponses().putIfAbsent("405", responseReference("MethodNotAllowed"));
                    operation.getResponses().putIfAbsent("406", responseReference("NotAcceptable"));
                    operation.getResponses().putIfAbsent("415", responseReference("UnsupportedMediaType"));
                    operation.getResponses().putIfAbsent("500", responseReference("InternalServerError"));
                })
        );
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .addHeaderObject(REQUEST_ID_HEADER, new Header().$ref("#/components/headers/RequestId"))
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(new ObjectSchema().$ref("#/components/schemas/ApiErrorResponse"))
                ));
    }

    private ApiResponse responseReference(String name) {
        return new ApiResponse().$ref("#/components/responses/" + name);
    }
}
