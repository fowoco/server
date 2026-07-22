package com.fowoco.server.auth.api;

import com.fowoco.server.auth.application.AuthService;
import com.fowoco.server.auth.application.LoginResult;
import com.fowoco.server.auth.application.RefreshResult;
import com.fowoco.server.auth.application.error.InvalidRefreshTokenException;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "사업장 사용자 로그인과 현재 인증 정보")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final ActorContextProvider actorContextProvider;

    public AuthController(
            AuthService authService,
            RefreshTokenCookieFactory refreshTokenCookieFactory,
            ActorContextProvider actorContextProvider
    ) {
        this.authService = authService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "login",
            summary = "사업장 사용자 로그인",
            description = "이메일과 비밀번호를 검증해 Access Token을 응답 본문에, "
                    + "Refresh Token을 HttpOnly 쿠키에 발급합니다. 실패 원인은 하나의 메시지로 통일합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    headers = {
                            @Header(
                                    name = HttpHeaders.SET_COOKIE,
                                    ref = "#/components/headers/RefreshTokenCookie"
                            ),
                            @Header(
                                    name = HttpHeaders.CACHE_CONTROL,
                                    description = "토큰 응답 저장 방지",
                                    schema = @Schema(type = "string", example = "no-store")
                            ),
                            @Header(
                                    name = HttpHeaders.PRAGMA,
                                    description = "구형 캐시의 토큰 응답 저장 방지",
                                    schema = @Schema(type = "string", example = "no-cache")
                            )
                    },
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LoginResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
            @ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType")
    })
    @PostMapping(
            path = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request.toCommand());
        ResponseCookie refreshTokenCookie = refreshTokenCookieFactory.create(result.refreshToken());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(LoginResponse.from(result));
    }

    @Operation(
            operationId = "refreshAccessToken",
            summary = "Access Token 재발급",
            description = "HttpOnly 쿠키의 Refresh Token을 한 번 사용하고 새 Access Token과 "
                    + "새 Refresh Token 쿠키로 회전합니다. 요청 본문과 Bearer Access Token은 사용하지 않습니다. "
                    + "누락·만료·폐기·재사용은 같은 401로 응답하며 Client는 재발급 요청을 한 번에 하나만 보냅니다.",
            security = @SecurityRequirement(name = "refreshCookie")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 회전 성공",
                    headers = {
                            @Header(
                                    name = HttpHeaders.SET_COOKIE,
                                    ref = "#/components/headers/RefreshTokenCookie"
                            ),
                            @Header(
                                    name = HttpHeaders.CACHE_CONTROL,
                                    description = "토큰 응답 저장 방지",
                                    schema = @Schema(type = "string", example = "no-store")
                            ),
                            @Header(
                                    name = HttpHeaders.PRAGMA,
                                    description = "구형 캐시의 토큰 응답 저장 방지",
                                    schema = @Schema(type = "string", example = "no-cache")
                            )
                    },
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RefreshResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidRefreshToken")
    })
    @PostMapping(path = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RefreshResponse> refresh(
            @Parameter(
                    name = "fowoco_refresh_token",
                    description = "로그인 또는 직전 재발급 응답에서 받은 HttpOnly Refresh Token 쿠키",
                    in = ParameterIn.COOKIE,
                    required = true
            )
            @CookieValue(
                    name = "${app.auth.refresh-token.cookie.name}",
                    required = false
            ) String rawRefreshToken,
            HttpServletResponse response
    ) {
        try {
            RefreshResult result = authService.refresh(rawRefreshToken);
            ResponseCookie refreshTokenCookie = refreshTokenCookieFactory.create(result.refreshToken());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                    .body(RefreshResponse.from(result));
        } catch (InvalidRefreshTokenException exception) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString());
            throw exception;
        }
    }

    @Operation(
            operationId = "logout",
            summary = "로그아웃",
            description = "Refresh Token 묶음을 폐기하고 쿠키를 삭제합니다. "
                    + "요청 본문과 Bearer Access Token은 사용하지 않으며, 토큰이 없거나 이미 폐기되었어도 같은 204로 응답합니다. "
                    + "기존 Access Token은 현재 기본 설정에서 만료까지 최대 15분간 유효하므로 Client가 즉시 삭제해야 합니다."
    )
    @ApiResponse(
            responseCode = "204",
            description = "로그아웃 처리 완료",
            headers = {
                    @Header(
                            name = HttpHeaders.SET_COOKIE,
                            ref = "#/components/headers/ExpiredRefreshTokenCookie"
                    ),
                    @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "로그아웃 응답 저장 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    @Header(
                            name = HttpHeaders.PRAGMA,
                            description = "구형 캐시의 로그아웃 응답 저장 방지",
                            schema = @Schema(type = "string", example = "no-cache")
                    )
            }
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(
                    name = "fowoco_refresh_token",
                    description = "있으면 해당 토큰 묶음을 폐기합니다. 없어도 로그아웃은 204로 성공합니다.",
                    in = ParameterIn.COOKIE,
                    required = false
            )
            @CookieValue(
                    name = "${app.auth.refresh-token.cookie.name}",
                    required = false
            ) String rawRefreshToken
    ) {
        authService.logout(rawRefreshToken);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .build();
    }

    @Operation(
            operationId = "getCurrentActor",
            summary = "현재 인증 사용자 확인",
            description = "검증된 Access Token에서 user_id, company_id, roles만 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "현재 인증 사용자",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CurrentActorResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    })
    @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public CurrentActorResponse me() {
        return CurrentActorResponse.from(actorContextProvider.requireCurrentActor());
    }
}
