package com.travel.routing.util;

import com.travel.routing.dto.RoutePoint;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

public final class RoutePathCodec {

    private static final JsonMapper JSON_MAPPER =
            JsonMapper.builder().build();

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
            return JSON_MAPPER.writeValueAsString(points);
        } catch (JacksonException e) {
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
            return JSON_MAPPER.readValue(
                    json,
                    ROUTE_POINT_LIST_TYPE
            );
        } catch (JacksonException e) {
            return List.of();
        }
    }
}
