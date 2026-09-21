package com.travel.user.dto;

import com.travel.user.entity.User;

public record UserResponse(
        Long id,
        String email,
        String nickname
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname()
        );
    }
}