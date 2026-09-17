package com.travel.user.controller;

import com.travel.global.response.ApiResponse;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.user.dto.UserLoginRequest;
import com.travel.user.dto.UserLoginResponse;
import com.travel.user.dto.UserResponse;
import com.travel.user.dto.UserSignUpRequest;
import com.travel.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Tag(
        name = "사용자",
        description = "사용자 관련 API"
)
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final String REFRESH_COOKIE_NAME =
            "tripbuddy.refreshToken";

    private final UserService userService;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${jwt.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    @Value("${jwt.refresh-cookie-same-site:Lax}")
    private String refreshCookieSameSite;

    @Operation(
            summary = "회원가입",
            description = "새로운 사용자를 등록합니다."
    )
    @PostMapping("/signup")
    public ApiResponse<UserResponse> signUp(
            @Valid @RequestBody UserSignUpRequest request
    ) {

        UserResponse response =
                userService.signUp(request);

        return ApiResponse.success(
                "회원가입이 완료되었습니다.",
                response
        );
    }
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserLoginResponse>> login(
            @Valid @RequestBody UserLoginRequest request
    ) {
        UserService.AuthenticationTokens tokens = userService.login(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken()).toString())
                .body(ApiResponse.success(
                        "로그인에 성공했습니다.",
                        UserLoginResponse.of(tokens.accessToken())
                ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<UserLoginResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        UserService.AuthenticationTokens tokens = userService.refresh(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken()).toString())
                .body(ApiResponse.success(
                        "인증 토큰이 갱신되었습니다.",
                        UserLoginResponse.of(tokens.accessToken())
                ));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        userService.logout(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .body(ApiResponse.success("로그아웃되었습니다."));
    }

    private ResponseCookie refreshCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/users")
                .maxAge(Duration.ofMillis(refreshTokenExpiration))
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/users")
                .maxAge(Duration.ZERO)
                .build();
    }
}
