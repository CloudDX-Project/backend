package com.travel.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(
            HttpStatus.BAD_REQUEST,
            "잘못된 입력값입니다."
    ),

    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "서버 내부 오류가 발생했습니다."
    ),

    // User
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "사용자를 찾을 수 없습니다."
    ),

    DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
            "이미 사용 중인 이메일입니다."
    ),

    // Auth
    INVALID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "유효하지 않은 토큰입니다."
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "인증이 필요합니다."
    ),

    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "접근 권한이 없습니다."
    ),

    // Trip
    TRIP_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "여행 정보를 찾을 수 없습니다."
    ),

    INVALID_TRIP_PERIOD(
            HttpStatus.BAD_REQUEST,
            "종료일은 출발일보다 빠를 수 없습니다."
    ),

    TRIP_DAY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "여행 일자 정보를 찾을 수 없습니다."
    ),

    TRANSPORT_SEGMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "이동 구간 정보를 찾을 수 없습니다."
    ),

    INVALID_TRANSPORT_SEGMENT_DATE(
            HttpStatus.BAD_REQUEST,
            "이동 구간의 출발 일자가 해당 여행 일자와 일치하지 않습니다."
    ),

    INVALID_TRANSPORT_SEGMENT_TIME(
            HttpStatus.BAD_REQUEST,
            "도착 시간은 출발 시간보다 빠를 수 없습니다."
    ),

    INVALID_TRANSPORT_SEGMENT_ORDER(
            HttpStatus.BAD_REQUEST,
            "이동 구간 순서 정보가 올바르지 않습니다."
    ),

    // Vehicle
    VEHICLE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "차량 정보를 찾을 수 없습니다."
    ),

    // External API
    EXTERNAL_API_ERROR(
            HttpStatus.BAD_GATEWAY,
            "외부 API 호출 중 오류가 발생했습니다."
    ),

    INVALID_LOGIN(
            HttpStatus.UNAUTHORIZED,
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),
    INVALID_TRIP_TIME(
            HttpStatus.BAD_REQUEST,
            "같은 날짜인 경우 종료 시간은 시작 시간보다 늦어야 합니다."
    );

    private final HttpStatus status;
    private final String message;

    ErrorCode(
            HttpStatus status,
            String message
    ) {
        this.status = status;
        this.message = message;
    }
}