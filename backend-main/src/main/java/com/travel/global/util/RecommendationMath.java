package com.travel.global.util;

/**
 * 장소 검색과 추천에서 공통으로 사용하는 거리·점수 계산식을 모은다.
 */
public final class RecommendationMath {
    private static final double EARTH_RADIUS_KM = 6_371.0;

    private RecommendationMath() {
    }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2.0) * Math.sin(latDistance / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2.0) * Math.sin(lonDistance / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static int estimatedDriveMinutes(double distanceKm, double speedKmh) {
        if (speedKmh <= 0.0) {
            throw new IllegalArgumentException("평균 속도는 0보다 커야 합니다.");
        }
        return (int) Math.ceil(distanceKm / speedKmh * 60.0);
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double round(double value, int digits) {
        double scale = Math.pow(10, digits);
        return Math.round(value * scale) / scale;
    }
}
