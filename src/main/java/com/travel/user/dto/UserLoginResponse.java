package com.travel.user.dto;

public record UserLoginResponse(
        String accessToken,
        String tokenType
) {

    public static UserLoginResponse of(String accessToken) {
        return new UserLoginResponse(
                accessToken,
                "Bearer"
        );
    }
}