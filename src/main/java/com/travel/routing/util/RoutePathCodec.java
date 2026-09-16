package com.travel.routing.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.routing.dto.RoutePoint;

import java.util.List;

public final class RoutePathCodec {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    private static final TypeReference<List<RoutePoint>> ROUTE_POINT_LIST_TYPE =
            new TypeReference<>() {
            };

    private RoutePathCodec() {
    }

    public static String encode(
            List<RoutePoint> points
    ) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(points);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "이동 경로 좌표를 JSON으로 변환하지 못했습니다.",
                    e
            );
        }
    }

    public static List<RoutePoint> decode(
            String json
    ) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return OBJECT_MAPPER.readValue(
                    json,
                    ROUTE_POINT_LIST_TYPE
            );
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
