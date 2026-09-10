package com.travel.trip.plan.service;

import com.travel.accommodation.data.AccommodationEnrichmentData;
import com.travel.accommodation.repository.AccommodationEnrichmentRepository;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.entity.FoodPreference;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripPreference;
import com.travel.trip.plan.dto.TripPlanCandidatePool;
import com.travel.trip.plan.dto.TripPlanSelectedAccommodation;
import com.travel.weather.DailyWeatherResponse;
import com.travel.weather.WeatherCondition;
import com.travel.weather.WeatherService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TripPlanCandidateService {

    private static final String ACCOMMODATION_PROVIDER =
            "NAVER_HOTEL";

    private final AccommodationEnrichmentRepository accommodationEnrichmentRepository;
    private final WeatherService weatherService;
    private final TripPlanDomainCandidateCacheService domainCandidateCacheService;

    public TripPlanCandidateService(
            AccommodationEnrichmentRepository accommodationEnrichmentRepository,
            WeatherService weatherService,
            TripPlanDomainCandidateCacheService domainCandidateCacheService
    ) {
        this.accommodationEnrichmentRepository =
                accommodationEnrichmentRepository;
        this.weatherService =
                weatherService;
        this.domainCandidateCacheService =
                domainCandidateCacheService;
    }

    public TripPlanCandidatePool buildCandidatePool(
            Long userId,
            Trip trip,
            Long accommodationId
    ) {

        TripPlanSelectedAccommodation accommodation =
                resolveSelectedAccommodation(
                        accommodationId
                );

        List<DailyWeatherResponse> weather =
                weatherService.getTripWeather(
                        userId,
                        trip.getId()
                );

        WeatherCondition planningWeather =
                resolvePlanningWeather(
                        weather
                );

        /*
         * 현재 DB에는 TripPreference.CAFE가 남아 있지만
         * 실제 프론트 흐름에서는 카페는 FoodPreference.CAFE다.
         * 일정 생성 후보에서는 일반 여행 테마 CAFE를 제외한다.
         */
        List<TripPreference> attractionPreferences =
                trip.getPreferences()
                        .stream()
                        .filter(
                                preference ->
                                        preference != TripPreference.CAFE
                        )
                        .toList();

        Set<TripPreference> commonPreferences =
                new HashSet<>(
                        attractionPreferences
                );

        Set<FoodPreference> restaurantFoodPreferences =
                new HashSet<>(
                        trip.getFoodPreferences()
                );

        /*
         * CAFE는 식당 category 필터에 넣지 않는다.
         */
        restaurantFoodPreferences.remove(
                FoodPreference.CAFE
        );

        String baseCacheKey =
                buildBaseCacheKey(
                        trip,
                        accommodation
                );

        List<TripPlanCandidatePool.AttractionCandidate> attractions =
                domainCandidateCacheService
                        .getAttractionCandidates(
                                baseCacheKey
                                        + ":weather:"
                                        + planningWeather.name(),
                                accommodation.latitude(),
                                accommodation.longitude(),
                                attractionPreferences,
                                planningWeather,
                                trip.getPace()
                        );

        List<TripPlanCandidatePool.RestaurantCandidate> restaurants =
                domainCandidateCacheService
                        .getRestaurantCandidates(
                                baseCacheKey,
                                accommodation.latitude(),
                                accommodation.longitude(),
                                commonPreferences,
                                restaurantFoodPreferences
                        );

        List<TripPlanCandidatePool.CafeCandidate> cafes;

        if (
                trip.getFoodPreferences()
                        .contains(
                                FoodPreference.CAFE
                        )
        ) {

            cafes =
                    domainCandidateCacheService
                            .getCafeCandidates(
                                    baseCacheKey,
                                    accommodation.latitude(),
                                    accommodation.longitude(),
                                    commonPreferences
                            );

        } else {

            cafes =
                    List.of();
        }

        return new TripPlanCandidatePool(
                accommodation,
                List.copyOf(attractions),
                List.copyOf(restaurants),
                List.copyOf(cafes),
                List.copyOf(weather)
        );
    }

    private TripPlanSelectedAccommodation
    resolveSelectedAccommodation(
            Long accommodationId
    ) {

        AccommodationEnrichmentData data =
                accommodationEnrichmentRepository
                        .findByAccommodationIdAndProvider(
                                accommodationId,
                                ACCOMMODATION_PROVIDER
                        )
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.TRIP_PLAN_ACCOMMODATION_NOT_FOUND
                                        )
                        );

        if (
                data.providerLatitude() == null
                        ||
                        data.providerLongitude() == null
        ) {

            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_ACCOMMODATION_COORDINATES_MISSING
            );
        }

        return new TripPlanSelectedAccommodation(
                data.accommodationId(),
                data.providerId(),
                data.providerName(),
                data.providerAddress(),
                data.providerLatitude(),
                data.providerLongitude(),
                data.checkInTime(),
                data.checkOutTime()
        );
    }

    private String buildBaseCacheKey(
            Trip trip,
            TripPlanSelectedAccommodation accommodation
    ) {

        return "v1:trip:"
                + trip.getId()
                + ":updated:"
                + trip.getUpdatedAt()
                + ":accommodation:"
                + accommodation.accommodationId();
    }

    /*
     * AttractionRecommendationService는 현재 하루 단위 날씨가 아니라
     * 하나의 WeatherCondition을 받는다.
     *
     * 후보군 생성 단계에서는 여행 전체 중 가장 영향이 큰 날씨를 하나 고르고,
     * 최종 Planner에는 일자별 weather 전체를 다시 전달한다.
     */
    private WeatherCondition resolvePlanningWeather(
            List<DailyWeatherResponse> weather
    ) {

        if (weather == null || weather.isEmpty()) {
            return WeatherCondition.UNKNOWN;
        }

        List<WeatherCondition> conditions =
                new ArrayList<>();

        for (DailyWeatherResponse day : weather) {
            if (day != null && day.condition() != null) {
                conditions.add(
                        day.condition()
                );
            }
        }

        if (conditions.contains(WeatherCondition.SNOW)) {
            return WeatherCondition.SNOW;
        }

        if (conditions.contains(WeatherCondition.RAIN)) {
            return WeatherCondition.RAIN;
        }

        if (conditions.contains(WeatherCondition.CLOUDY)) {
            return WeatherCondition.CLOUDY;
        }

        if (conditions.contains(WeatherCondition.SUNNY)) {
            return WeatherCondition.SUNNY;
        }

        return WeatherCondition.UNKNOWN;
    }
}