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


    TRIP_PLAN_ACCOMMODATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "선택한 숙소 정보를 찾을 수 없습니다."
    ),

    TRIP_PLAN_ACCOMMODATION_COORDINATES_MISSING(
            HttpStatus.BAD_REQUEST,
            "선택한 숙소의 위도/경도 정보가 없습니다."
    ),

    TRIP_PLAN_FLIGHT_SELECTION_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "항공 여행은 가는 편과 오는 편 항공편 선택이 필요합니다."
    ),

    TRIP_PLAN_INVALID_FLIGHT_SELECTION(
            HttpStatus.BAD_REQUEST,
            "선택한 항공편이 여행 정보와 일치하지 않습니다."
    ),
    TRIP_PLAN_RENTAL_SELECTION_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "렌터카 이용 여행은 렌터카 선택이 필요합니다."
    ),

    TRIP_PLAN_INVALID_RENTAL_SELECTION(
            HttpStatus.BAD_REQUEST,
            "선택한 렌터카 위치 또는 셔틀 소요시간 정보가 올바르지 않습니다."
    ),

    TRIP_PLAN_AIRPORT_UNREACHABLE(
            HttpStatus.BAD_REQUEST,
            "선택한 항공편의 공항 도착 마감을 지킬 수 없습니다. 항공편 또는 숙소를 변경해주세요."
    ),

    TRIP_PLAN_REQUEST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "요청된 여행 일정 생성 작업을 찾을 수 없습니다."
    ),

    TRIP_PLAN_QUEUE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "여행 일정 생성 요청을 전달할 수 없습니다. 잠시 후 다시 시도해주세요."
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


    // Restaurant / Cafe
    ATTRACTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "관광지를 찾을 수 없습니다."
    ),

    RESTAURANT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "식당 정보를 찾을 수 없습니다."
    ),

    CAFE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "카페 정보를 찾을 수 없습니다."
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
    ),
    WEATHER_API_KEY_NOT_CONFIGURED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "기상청 API 인증키가 설정되지 않았습니다."
    ),

    WEATHER_API_ERROR(
            HttpStatus.BAD_GATEWAY,
            "기상청 날씨 API 호출 중 오류가 발생했습니다."
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
