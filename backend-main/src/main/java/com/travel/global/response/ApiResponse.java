package com.travel.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true,
                "요청이 성공적으로 처리되었습니다.",
                data
        );
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
                true,
                message,
                data
        );
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(
                true,
                message,
                null
        );
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(
                false,
                message,
                null
        );
    }
}