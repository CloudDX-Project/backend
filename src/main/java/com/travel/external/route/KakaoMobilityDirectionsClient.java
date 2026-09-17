package com.travel.external.route;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.travel.routing.dto.DrivingRouteResult;
import com.travel.routing.dto.RoutePoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Component
public class KakaoMobilityDirectionsClient {

    private final RestClient restClient;
    private final String restApiKey;

    public KakaoMobilityDirectionsClient(
            @Value("${external.kakao-mobility.base-url:https://apis-navi.kakaomobility.com}")
            String baseUrl,

            @Value("${external.kakao-mobility.rest-api-key:}")
            String restApiKey
    ) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .build();

        this.restApiKey = restApiKey;
    }

    @Cacheable(
            value = "kakaoDrivingRoute",
            key = "#originLatitude + ':' + #originLongitude + ':' + "
                    + "#destinationLatitude + ':' + #destinationLongitude",
            unless = "#result == null"
    )
    public DrivingRouteResult findRoute(
            Double originLatitude,
            Double originLongitude,
            Double destinationLatitude,
            Double destinationLongitude
    ) {
        validateApiKey();
        validateCoordinates(
                originLatitude,
                originLongitude,
                destinationLatitude,
                destinationLongitude
        );

        String origin =
                originLongitude + "," + originLatitude;

        String destination =
                destinationLongitude + "," + destinationLatitude;

        try {
            KakaoDirectionsResponse response =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/v1/directions")
                                                    .queryParam("origin", origin)
                                                    .queryParam("destination", destination)
                                                    .queryParam("priority", "RECOMMEND")
                                                    .queryParam("alternatives", false)
                                                    .queryParam("road_details", false)
                                                    .queryParam("summary", false)
                                                    .build()
                            )
                            .header(
                                    "Authorization",
                                    "KakaoAK " + restApiKey
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .retrieve()
                            .body(KakaoDirectionsResponse.class);

            return convert(response);

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
                        "Kakao Mobility 길찾기 API 호출 한도를 초과했습니다.",
                        e
                );
            }

            throw new IllegalStateException(
                    "Kakao Mobility 길찾기 API 호출에 실패했습니다. HTTP "
                            + status,
                    e
            );

        } catch (RestClientException e) {
            throw new IllegalStateException(
                    "Kakao Mobility 길찾기 API 연결에 실패했습니다.",
                    e
            );
        }
    }

    private DrivingRouteResult convert(
            KakaoDirectionsResponse response
    ) {
        if (
                response == null
                        || response.routes() == null
                        || response.routes().isEmpty()
        ) {
            throw new IllegalStateException(
                    "Kakao Mobility 길찾기 결과가 없습니다."
            );
        }

        KakaoRoute route =
                response.routes()
                        .stream()
                        .filter(item ->
                                item != null
                                        && item.resultCode() != null
                                        && item.resultCode() == 0
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Kakao Mobility 경로 탐색에 실패했습니다: "
                                                + firstRouteMessage(response.routes())
                                )
                        );

        if (route.summary() == null) {
            throw new IllegalStateException(
                    "Kakao Mobility 경로 요약 정보가 없습니다."
            );
        }

        List<RoutePoint> path =
                extractPath(route.sections());

        KakaoFare fare =
                route.summary().fare();

        return new DrivingRouteResult(
                safeLong(route.summary().distance()),
                safeLong(route.summary().duration()),
                fare == null ? 0L : safeLong(fare.taxi()),
                fare == null ? 0L : safeLong(fare.toll()),
                path
        );
    }

    private List<RoutePoint> extractPath(
            List<KakaoSection> sections
    ) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }

        List<RoutePoint> result =
                new ArrayList<>();

        for (KakaoSection section : sections) {
            if (section == null || section.roads() == null) {
                continue;
            }

            for (KakaoRoad road : section.roads()) {
                if (road == null || road.vertexes() == null) {
                    continue;
                }

                List<Double> vertexes =
                        road.vertexes();

                for (int i = 0; i + 1 < vertexes.size(); i += 2) {
                    Double longitude = vertexes.get(i);
                    Double latitude = vertexes.get(i + 1);

                    if (latitude == null || longitude == null) {
                        continue;
                    }

                    RoutePoint point =
                            new RoutePoint(
                                    latitude,
                                    longitude
                            );

                    if (
                            result.isEmpty()
                                    || !samePoint(
                                    result.get(result.size() - 1),
                                    point
                            )
                    ) {
                        result.add(point);
                    }
                }
            }
        }

        return result;
    }

    private boolean samePoint(
            RoutePoint a,
            RoutePoint b
    ) {
        return Double.compare(a.latitude(), b.latitude()) == 0
                && Double.compare(a.longitude(), b.longitude()) == 0;
    }

    private String firstRouteMessage(
            List<KakaoRoute> routes
    ) {
        return routes.stream()
                .filter(route -> route != null && route.resultMessage() != null)
                .map(KakaoRoute::resultMessage)
                .findFirst()
                .orElse("알 수 없는 오류");
    }

    private long safeLong(
            Number value
    ) {
        return value == null
                ? 0L
                : value.longValue();
    }

    private void validateApiKey() {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new IllegalStateException(
                    "KAKAO_REST_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }
    }

    private void validateCoordinates(
            Double originLatitude,
            Double originLongitude,
            Double destinationLatitude,
            Double destinationLongitude
    ) {
        if (
                originLatitude == null
                        || originLongitude == null
                        || destinationLatitude == null
                        || destinationLongitude == null
        ) {
            throw new IllegalArgumentException(
                    "길찾기에는 출발지와 도착지 위도/경도가 필요합니다."
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoDirectionsResponse(
            @JsonProperty("trans_id")
            String transId,
            List<KakaoRoute> routes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoRoute(
            @JsonProperty("result_code")
            Integer resultCode,

            @JsonProperty("result_msg")
            String resultMessage,

            KakaoSummary summary,
            List<KakaoSection> sections
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoSummary(
            KakaoFare fare,
            Long distance,
            Long duration
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoFare(
            Long taxi,
            Long toll
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoSection(
            Long distance,
            Long duration,
            List<KakaoRoad> roads
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoRoad(
            String name,
            Long distance,
            Long duration,

            @JsonProperty("vertexes")
            List<Double> vertexes
    ) {
    }
}
