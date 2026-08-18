package com.travel.user.controller;

import com.travel.global.response.ApiResponse;
import com.travel.user.dto.UserResponse;
import com.travel.user.dto.UserSignUpRequest;
import com.travel.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "사용자",
        description = "사용자 관련 API"
)
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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
}