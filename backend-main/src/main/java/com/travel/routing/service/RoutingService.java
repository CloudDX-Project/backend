package com.travel.routing.service;

import com.travel.external.route.KakaoMobilityDirectionsClient;
import com.travel.routing.dto.DrivingRouteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class RoutingService {

    private static final double SAME_LOCATION_THRESHOLD_METERS = 5.0;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final KakaoMobilityDirectionsClient
            kakaoMobilityDirectionsClient;

    // 일정 생성 한 번 안에서는 같은 구간의 시간과 path를 끝까지 재사용한다.
    // 실패도 재사용하여 후처리/저장 단계에서 같은 API를 반복 호출하지 않는다.
    private final ThreadLocal<Map<RouteKey, Lookup>> snapshot = new ThreadLocal<>();

    public <T> T withSnapshot(Supplier<T> action) {
        if (snapshot.get() != null) return action.get();
        snapshot.set(new HashMap<>());
        try {
            return action.get();
        } finally {
            snapshot.remove();
        }
    }

    public DrivingRouteResult findDrivingRoute(
            Double originLatitude,
            Double originLongitude,
            Double destinationLatitude,
            Double destinationLongitude
    ) {
        if (isSameLocation(originLatitude, originLongitude,
                destinationLatitude, destinationLongitude)) {
            return new DrivingRouteResult(0, 0, 0, 0, java.util.List.of());
        }

        Map<RouteKey, Lookup> routes = snapshot.get();
        RouteKey key = new RouteKey(originLatitude, originLongitude, destinationLatitude, destinationLongitude);
        if (routes == null) {
            return loadRoute(key);
        }
        Lookup lookup = routes.computeIfAbsent(key, ignored -> {
            try {
                return new Lookup(loadRoute(key), null);
            } catch (RuntimeException exception) {
                return new Lookup(null, exception);
            }
        });
        if (lookup.failure() != null) throw lookup.failure();
        return lookup.route();
    }

    public boolean isSameLocation(
            Double originLatitude,
            Double originLongitude,
            Double destinationLatitude,
            Double destinationLongitude
    ) {
        if (originLatitude == null || originLongitude == null
                || destinationLatitude == null || destinationLongitude == null) {
            return false;
        }

        double lat1 = Math.toRadians(originLatitude);
        double lat2 = Math.toRadians(destinationLatitude);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(destinationLongitude - originLongitude);
        double sinLat = Math.sin(deltaLat / 2.0);
        double sinLon = Math.sin(deltaLon / 2.0);
        double a = sinLat * sinLat
                + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        double distance = 2.0 * EARTH_RADIUS_METERS
                * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return distance <= SAME_LOCATION_THRESHOLD_METERS;
    }

    private DrivingRouteResult loadRoute(RouteKey key) {
        return kakaoMobilityDirectionsClient.findRoute(
                key.originLatitude(), key.originLongitude(), key.destinationLatitude(), key.destinationLongitude()
        );
    }

    private record RouteKey(Double originLatitude, Double originLongitude,
                            Double destinationLatitude, Double destinationLongitude) {}
    private record Lookup(DrivingRouteResult route, RuntimeException failure) {}
}
