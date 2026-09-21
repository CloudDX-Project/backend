package com.travel.trip.plan.service;

import com.travel.external.route.KakaoMobilityMultiDestinationClient;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.routing.dto.RouteSummary;
import com.travel.trip.entity.FoodPreference;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripAccommodationSelection;
import com.travel.trip.entity.TripPreference;
import com.travel.trip.plan.dto.TripPlanCandidatePool;
import com.travel.trip.plan.dto.TripPlanSelectedAccommodation;
import com.travel.weather.DailyWeatherResponse;
import com.travel.weather.WeatherCondition;
import com.travel.weather.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TripPlanCandidateService {

    private static final Logger log =
            LoggerFactory.getLogger(TripPlanCandidateService.class);

    private static final long DESTINATION_ID_BASE =
            9_000_000_000_000L;

    private final WeatherService weatherService;
    private final TripPlanDomainCandidateCacheService domainCandidateCacheService;
    private final KakaoMobilityMultiDestinationClient multiDestinationClient;

    public TripPlanCandidateService(
            WeatherService weatherService,
            TripPlanDomainCandidateCacheService domainCandidateCacheService,
            KakaoMobilityMultiDestinationClient multiDestinationClient
    ) {
        this.weatherService = weatherService;
        this.domainCandidateCacheService = domainCandidateCacheService;
        this.multiDestinationClient = multiDestinationClient;
    }

    public TripPlanCandidatePool buildCandidatePool(
            Long userId,
            Trip trip
    ) {
        TripPlanSelectedAccommodation accommodation =
                resolveSelectedAccommodation(trip);

        List<DailyWeatherResponse> weather =
                weatherService.getTripWeather(
                        userId,
                        trip.getId()
                );

        WeatherCondition planningWeather =
                resolvePlanningWeather(weather);

        List<TripPreference> attractionPreferences =
                trip.getPreferences()
                        .stream()
                        .filter(preference -> preference != TripPreference.CAFE)
                        .toList();

        Set<TripPreference> commonPreferences =
                new HashSet<>(attractionPreferences);

        Set<FoodPreference> restaurantFoodPreferences =
                new HashSet<>(trip.getFoodPreferences());

        restaurantFoodPreferences.remove(FoodPreference.CAFE);

        String baseCacheKey =
                buildBaseCacheKey(
                        trip,
                        accommodation
                );

        List<TripPlanCandidatePool.AttractionCandidate> attractions =
                new ArrayList<>(
                        domainCandidateCacheService.getAttractionCandidates(
                                baseCacheKey + ":weather:" + planningWeather.name(),
                                accommodation.latitude(),
                                accommodation.longitude(),
                                attractionPreferences,
                                planningWeather,
                                trip.getPace()
                        )
                );

        List<TripPlanCandidatePool.RestaurantCandidate> restaurants =
                new ArrayList<>(
                        domainCandidateCacheService.getRestaurantCandidates(
                                baseCacheKey,
                                accommodation.latitude(),
                                accommodation.longitude(),
                                commonPreferences,
                                restaurantFoodPreferences
                        )
                );

        /*
         * 카페 선호 여부와 무관하게 후보 자체는 확보한다.
         * CAFE 선호는 Bedrock에서 방문 빈도를 결정하는 신호로 사용한다.
         */
        List<TripPlanCandidatePool.CafeCandidate> cafes =
                new ArrayList<>(
                        domainCandidateCacheService.getCafeCandidates(
                                baseCacheKey,
                                accommodation.latitude(),
                                accommodation.longitude(),
                                commonPreferences
                        )
                );

        TripPlanCandidatePool.MandatoryDestination mandatoryDestination =
                buildMandatoryDestination(trip);

        if (mandatoryDestination != null) {
            attractions = ensureMandatoryDestination(
                    attractions,
                    mandatoryDestination
            );
        }

        /*
         * Bedrock이 직선거리 추정값이 아니라 Kakao Mobility의 실제 도로
         * 거리/시간을 보고 장소를 고를 수 있도록 후보를 enrich한다.
         * 다중 목적지 API는 1회에 최대 30개를 처리하므로 후보를 chunk 처리한다.
         */
        RouteContext accommodationRoutes =
                loadRouteContext(
                        accommodation.latitude(),
                        accommodation.longitude(),
                        attractions,
                        restaurants,
                        cafes
                );

        RouteContext destinationRoutes =
                mandatoryDestination == null
                        ? RouteContext.empty()
                        : loadRouteContext(
                        mandatoryDestination.latitude(),
                        mandatoryDestination.longitude(),
                        attractions,
                        restaurants,
                        cafes
                );

        attractions = enrichAttractions(
                attractions,
                accommodationRoutes,
                destinationRoutes
        );

        restaurants = enrichRestaurants(
                restaurants,
                accommodationRoutes,
                destinationRoutes
        );

        cafes = enrichCafes(
                cafes,
                accommodationRoutes,
                destinationRoutes
        );

        return new TripPlanCandidatePool(
                accommodation,
                mandatoryDestination,
                List.copyOf(attractions),
                List.copyOf(restaurants),
                List.copyOf(cafes),
                List.copyOf(weather)
        );
    }

    private TripPlanCandidatePool.MandatoryDestination buildMandatoryDestination(
            Trip trip
    ) {
        if (
                trip.getDestination() == null
                        || trip.getDestination().isBlank()
                        || trip.getDestinationLatitude() == null
                        || trip.getDestinationLongitude() == null
        ) {
            return null;
        }

        long syntheticId =
                DESTINATION_ID_BASE + trip.getId();

        return new TripPlanCandidatePool.MandatoryDestination(
                syntheticId,
                trip.getDestination().trim(),
                trip.getDestinationLatitude(),
                trip.getDestinationLongitude()
        );
    }

    private List<TripPlanCandidatePool.AttractionCandidate> ensureMandatoryDestination(
            List<TripPlanCandidatePool.AttractionCandidate> attractions,
            TripPlanCandidatePool.MandatoryDestination destination
    ) {
        for (TripPlanCandidatePool.AttractionCandidate item : attractions) {
            if (samePlaceName(item.name(), destination.name())) {
                ArrayList<TripPlanCandidatePool.AttractionCandidate> result =
                        new ArrayList<>();

                for (TripPlanCandidatePool.AttractionCandidate current : attractions) {
                    if (current.id().equals(item.id())) {
                        result.add(
                                new TripPlanCandidatePool.AttractionCandidate(
                                        current.id(),
                                        current.name(),
                                        current.category(),
                                        current.latitude(),
                                        current.longitude(),
                                        current.distanceKm(),
                                        current.estimatedDriveMinutes(),
                                        current.recommendationScore(),
                                        current.tags(),
                                        current.recommendationReason(),
                                        true,
                                        current.actualDistanceKmFromAccommodation(),
                                        current.actualDriveMinutesFromAccommodation(),
                                        current.actualDistanceKmFromDestination(),
                                        current.actualDriveMinutesFromDestination()
                                )
                        );
                    } else {
                        result.add(current);
                    }
                }
                return result;
            }
        }

        ArrayList<TripPlanCandidatePool.AttractionCandidate> result =
                new ArrayList<>();

        result.add(
                new TripPlanCandidatePool.AttractionCandidate(
                        destination.syntheticId(),
                        destination.name(),
                        "USER_DESTINATION",
                        destination.latitude(),
                        destination.longitude(),
                        null,
                        null,
                        1.0,
                        "사용자가 직접 선택한 목적지",
                        "사용자가 목적지로 선택했으므로 여행 전체에서 반드시 1회 방문",
                        true,
                        null,
                        null,
                        0.0,
                        0
                )
        );
        result.addAll(attractions);

        return result;
    }

    private RouteContext loadRouteContext(
            Double originLatitude,
            Double originLongitude,
            List<TripPlanCandidatePool.AttractionCandidate> attractions,
            List<TripPlanCandidatePool.RestaurantCandidate> restaurants,
            List<TripPlanCandidatePool.CafeCandidate> cafes
    ) {
        List<KakaoMobilityMultiDestinationClient.Destination> destinations =
                new ArrayList<>();

        attractions.forEach(item -> addDestination(
                destinations,
                attractionKey(item.id()),
                item.latitude(),
                item.longitude()
        ));
        restaurants.forEach(item -> addDestination(
                destinations,
                restaurantKey(item.id()),
                item.latitude(),
                item.longitude()
        ));
        cafes.forEach(item -> addDestination(
                destinations,
                cafeKey(item.id()),
                item.latitude(),
                item.longitude()
        ));

        Map<String, RouteSummary> result =
                new LinkedHashMap<>();

        for (int start = 0;
             start < destinations.size();
             start += KakaoMobilityMultiDestinationClient.MAX_DESTINATIONS) {

            int end = Math.min(
                    destinations.size(),
                    start + KakaoMobilityMultiDestinationClient.MAX_DESTINATIONS
            );

            try {
                result.putAll(
                        multiDestinationClient.findRoutes(
                                originLatitude,
                                originLongitude,
                                destinations.subList(start, end)
                        )
                );
            } catch (RuntimeException e) {
                /*
                 * 후보 생성 자체를 Kakao 일시 장애 때문에 실패시키지는 않는다.
                 * 최종 구간 생성에서는 기존 단일 Directions + fallback 정책이 적용된다.
                 */
                log.warn(
                        "Kakao 다중 목적지 후보 거리 계산 실패. 기존 추천 거리값으로 진행합니다.",
                        e
                );
            }
        }

        return new RouteContext(result);
    }

    private void addDestination(
            List<KakaoMobilityMultiDestinationClient.Destination> destinations,
            String key,
            Double latitude,
            Double longitude
    ) {
        if (latitude == null || longitude == null) {
            return;
        }
        destinations.add(
                new KakaoMobilityMultiDestinationClient.Destination(
                        key,
                        latitude,
                        longitude
                )
        );
    }

    private List<TripPlanCandidatePool.AttractionCandidate> enrichAttractions(
            List<TripPlanCandidatePool.AttractionCandidate> source,
            RouteContext accommodationRoutes,
            RouteContext destinationRoutes
    ) {
        return source.stream()
                .map(item -> {
                    RouteSummary fromAccommodation =
                            accommodationRoutes.get(attractionKey(item.id()));
                    RouteSummary fromDestination =
                            destinationRoutes.get(attractionKey(item.id()));

                    return new TripPlanCandidatePool.AttractionCandidate(
                            item.id(), item.name(), item.category(),
                            item.latitude(), item.longitude(),
                            item.distanceKm(), item.estimatedDriveMinutes(),
                            item.recommendationScore(), item.tags(),
                            item.recommendationReason(), item.mandatory(),
                            distance(fromAccommodation), duration(fromAccommodation),
                            distance(fromDestination), duration(fromDestination)
                    );
                })
                .toList();
    }

    private List<TripPlanCandidatePool.RestaurantCandidate> enrichRestaurants(
            List<TripPlanCandidatePool.RestaurantCandidate> source,
            RouteContext accommodationRoutes,
            RouteContext destinationRoutes
    ) {
        return source.stream()
                .map(item -> {
                    RouteSummary fromAccommodation =
                            accommodationRoutes.get(restaurantKey(item.id()));
                    RouteSummary fromDestination =
                            destinationRoutes.get(restaurantKey(item.id()));

                    return new TripPlanCandidatePool.RestaurantCandidate(
                            item.id(), item.name(), item.category(),
                            item.latitude(), item.longitude(),
                            item.distanceKm(), item.estimatedDriveMinutes(),
                            item.recommendationScore(), item.matchedFoodPreferences(),
                            item.summary(), item.tags(), item.recommendationReason(),
                            item.rating(), item.reviewCount(), item.businessHours(),
                            item.qualityScore(),
                            item.breakfastFitScore(), item.lunchFitScore(), item.dinnerFitScore(),
                            distance(fromAccommodation), duration(fromAccommodation),
                            distance(fromDestination), duration(fromDestination)
                    );
                })
                .toList();
    }

    private List<TripPlanCandidatePool.CafeCandidate> enrichCafes(
            List<TripPlanCandidatePool.CafeCandidate> source,
            RouteContext accommodationRoutes,
            RouteContext destinationRoutes
    ) {
        return source.stream()
                .map(item -> {
                    RouteSummary fromAccommodation =
                            accommodationRoutes.get(cafeKey(item.id()));
                    RouteSummary fromDestination =
                            destinationRoutes.get(cafeKey(item.id()));

                    return new TripPlanCandidatePool.CafeCandidate(
                            item.id(), item.name(), item.category(),
                            item.latitude(), item.longitude(),
                            item.distanceKm(), item.estimatedDriveMinutes(),
                            item.recommendationScore(), item.summary(), item.tags(),
                            item.recommendationReason(), item.rating(), item.reviewCount(),
                            item.businessHours(), item.qualityScore(),
                            distance(fromAccommodation), duration(fromAccommodation),
                            distance(fromDestination), duration(fromDestination)
                    );
                })
                .toList();
    }

    private Double distance(RouteSummary summary) {
        return summary == null ? null : summary.distanceKm();
    }

    private Integer duration(RouteSummary summary) {
        return summary == null ? null : summary.durationMinutes();
    }

    private String attractionKey(Long id) {
        return "A:" + id;
    }

    private String restaurantKey(Long id) {
        return "R:" + id;
    }

    private String cafeKey(Long id) {
        return "C:" + id;
    }

    static boolean samePlaceName(String first, String second) {
        String left = normalizeName(first);
        String right = normalizeName(second);

        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }

        if (left.equals(right)) {
            return true;
        }

        int shorterLength = Math.min(left.length(), right.length());
        return shorterLength >= 4
                && (left.endsWith(right) || right.endsWith(left));
    }

    private static String normalizeName(String value) {
        return value == null
                ? ""
                : value.replaceAll("[^가-힣a-zA-Z0-9]", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private TripPlanSelectedAccommodation resolveSelectedAccommodation(
            Trip trip
    ) {
        TripAccommodationSelection accommodation =
                trip.getSelectedAccommodation();

        if (accommodation == null) {
            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_ACCOMMODATION_NOT_FOUND
            );
        }

        if (
                accommodation.getLatitude() == null
                        || accommodation.getLongitude() == null
        ) {
            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_ACCOMMODATION_COORDINATES_MISSING
            );
        }

        return new TripPlanSelectedAccommodation(
                accommodation.getAccommodationId(),
                accommodation.getProviderId(),
                accommodation.getName(),
                accommodation.getAddress(),
                accommodation.getLatitude(),
                accommodation.getLongitude(),
                accommodation.getCheckInTime(),
                accommodation.getCheckOutTime()
        );
    }

    private String buildBaseCacheKey(
            Trip trip,
            TripPlanSelectedAccommodation accommodation
    ) {
        return "v3:trip:"
                + trip.getId()
                + ":updated:"
                + trip.getUpdatedAt()
                + ":accommodation:"
                + accommodation.accommodationId();
    }

    private WeatherCondition resolvePlanningWeather(
            List<DailyWeatherResponse> weather
    ) {
        if (weather == null || weather.isEmpty()) {
            return WeatherCondition.UNKNOWN;
        }

        List<WeatherCondition> conditions = new ArrayList<>();

        for (DailyWeatherResponse day : weather) {
            if (day != null && day.condition() != null) {
                conditions.add(day.condition());
            }
        }

        if (conditions.contains(WeatherCondition.SNOW)) return WeatherCondition.SNOW;
        if (conditions.contains(WeatherCondition.RAIN)) return WeatherCondition.RAIN;
        if (conditions.contains(WeatherCondition.CLOUDY)) return WeatherCondition.CLOUDY;
        if (conditions.contains(WeatherCondition.SUNNY)) return WeatherCondition.SUNNY;
        return WeatherCondition.UNKNOWN;
    }

    private record RouteContext(
            Map<String, RouteSummary> routes
    ) {
        static RouteContext empty() {
            return new RouteContext(Map.of());
        }

        RouteSummary get(String key) {
            return routes.get(key);
        }
    }
}
