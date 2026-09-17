package com.travel.external.route;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.travel.routing.dto.RouteSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KakaoMobilityMultiDestinationClient {

    public static final int MAX_DESTINATIONS = 30;

    private final RestClient restClient;
    private final String restApiKey;

    public KakaoMobilityMultiDestinationClient(
            @Value("${external.kakao-mobility.base-url:https://apis-navi.kakaomobility.com}")
            String baseUrl,
            @Value("${external.kakao-mobility.rest-api-key:}")
            String restApiKey
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.restApiKey = restApiKey;
    }

    public Map<String, RouteSummary> findRoutes(
            Double originLatitude,
            Double originLongitude,
            List<Destination> destinations
    ) {
        validateApiKey();

        if (originLatitude == null || originLongitude == null) {
            return Map.of();
        }
        if (destinations == null || destinations.isEmpty()) {
            return Map.of();
        }
        if (destinations.size() > MAX_DESTINATIONS) {
            throw new IllegalArgumentException(
                    "Kakao Mobility 다중 목적지는 한 번에 최대 30개까지 요청할 수 있습니다."
            );
        }

        List<Map<String, Object>> destinationBody = new ArrayList<>();
        for (Destination destination : destinations) {
            if (destination == null
                    || destination.key() == null
                    || destination.latitude() == null
                    || destination.longitude() == null) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", destination.key());
            item.put("x", destination.longitude());
            item.put("y", destination.latitude());
            destinationBody.add(item);
        }

        if (destinationBody.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("x", originLongitude);
        origin.put("y", originLatitude);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("origin", origin);
        request.put("destinations", destinationBody);
        request.put("radius", 10000);
        request.put("priority", "TIME");

        try {
            KakaoMultiDestinationResponse response = restClient
                    .post()
                    .uri("/v1/destinations/directions")
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(KakaoMultiDestinationResponse.class);

            if (response == null || response.routes() == null) {
                return Map.of();
            }

            Map<String, RouteSummary> result = new LinkedHashMap<>();
            for (KakaoDestinationRoute route : response.routes()) {
                if (route == null
                        || route.resultCode() == null
                        || route.resultCode() != 0
                        || route.key() == null
                        || route.summary() == null) {
                    continue;
                }

                long distanceMeters = route.summary().distance() == null
                        ? 0L
                        : route.summary().distance();
                long durationSeconds = route.summary().duration() == null
                        ? 0L
                        : route.summary().duration();

                double distanceKm = Math.round(distanceMeters / 100.0) / 10.0;
                int durationMinutes = (int) Math.max(
                        1L,
                        Math.round(durationSeconds / 60.0)
                );

                result.put(
                        route.key(),
                        new RouteSummary(
                                route.key(),
                                distanceKm,
                                durationMinutes
                        )
                );
            }

            return result;

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new IllegalStateException(
                        "Kakao Mobility REST API 키 또는 앱 권한을 확인해주세요.",
                        e
                );
            }
            if (status == 429) {
                throw new IllegalStateException(
                        "Kakao Mobility 다중 목적지 API 호출 한도를 초과했습니다.",
                        e
                );
            }
            throw new IllegalStateException(
                    "Kakao Mobility 다중 목적지 API 호출에 실패했습니다. HTTP " + status,
                    e
            );
        } catch (RestClientException e) {
            throw new IllegalStateException(
                    "Kakao Mobility 다중 목적지 API 연결에 실패했습니다.",
                    e
            );
        }
    }

    private void validateApiKey() {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new IllegalStateException(
                    "KAKAO_REST_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }
    }

    public record Destination(
            String key,
            Double latitude,
            Double longitude
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoMultiDestinationResponse(
            @JsonProperty("trans_id") String transId,
            List<KakaoDestinationRoute> routes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoDestinationRoute(
            @JsonProperty("result_code") Integer resultCode,
            @JsonProperty("result_msg") String resultMessage,
            String key,
            KakaoRouteSummary summary
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoRouteSummary(
            Long distance,
            Long duration
    ) {
    }
}
