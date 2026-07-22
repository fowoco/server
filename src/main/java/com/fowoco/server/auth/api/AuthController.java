package com.fowoco.server.auth.api;

import com.fowoco.server.auth.application.AuthService;
import com.fowoco.server.auth.application.LoginResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResult result = authService.login(request.toCommand());

        // Refresh Token은 Cookie로 설정
        // LoginResponse에는 Access Token만 포함

        return ResponseEntity.ok(LoginResponse.from(result));
    }
}