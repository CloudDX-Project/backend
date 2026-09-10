package com.travel.trip.plan.service;

import com.travel.attraction.AttractionRecommendationService;
import com.travel.attraction.dto.AttractionRecommendRequest;
import com.travel.attraction.dto.AttractionRecommendResponse;
import com.travel.cafe.CafeRecommendationService;
import com.travel.cafe.dto.CafeRecommendRequest;
import com.travel.cafe.dto.CafeRecommendResponse;
import com.travel.restaurant.RestaurantRecommendationService;
import com.travel.restaurant.dto.RestaurantRecommendRequest;
import com.travel.restaurant.dto.RestaurantRecommendResponse;
import com.travel.trip.entity.FoodPreference;
import com.travel.trip.entity.TripPace;
import com.travel.trip.entity.TripPreference;
import com.travel.trip.plan.dto.TripPlanCandidatePool;
import com.travel.weather.WeatherCondition;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class TripPlanDomainCandidateCacheService {

    private static final int DOMAIN_CANDIDATE_LIMIT = 30;

    private final AttractionRecommendationService attractionRecommendationService;
    private final RestaurantRecommendationService restaurantRecommendationService;
    private final CafeRecommendationService cafeRecommendationService;

    public TripPlanDomainCandidateCacheService(
            AttractionRecommendationService attractionRecommendationService,
            RestaurantRecommendationService restaurantRecommendationService,
            CafeRecommendationService cafeRecommendationService
    ) {
        this.attractionRecommendationService =
                attractionRecommendationService;
        this.restaurantRecommendationService =
                restaurantRecommendationService;
        this.cafeRecommendationService =
                cafeRecommendationService;
    }

    /*
     * 관광지 일정 생성용 후보군.
     *
     * AttractionRecommendationService의 기존 추천 흐름을 그대로 사용한다.
     * DB -> 자체 점수 -> Bedrock reranking까지 끝난 결과를
     * 최대 30개로 받아 Redis에 저장한다.
     */
    @Cacheable(
            cacheNames = "tripPlanAttractionCandidates",
            key = "#cacheKey"
    )
    public List<TripPlanCandidatePool.AttractionCandidate>
    getAttractionCandidates(
            String cacheKey,
            Double latitude,
            Double longitude,
            List<TripPreference> preferences,
            WeatherCondition weatherCondition,
            TripPace pace
    ) {

        AttractionRecommendResponse response =
                attractionRecommendationService.recommend(
                        new AttractionRecommendRequest(
                                latitude,
                                longitude,
                                preferences,
                                weatherCondition,
                                pace,
                                DOMAIN_CANDIDATE_LIMIT
                        )
                );

        ArrayList<TripPlanCandidatePool.AttractionCandidate> result =
                new ArrayList<>();

        response.attractions()
                .forEach(
                        item ->
                                result.add(
                                        new TripPlanCandidatePool.AttractionCandidate(
                                                item.id(),
                                                item.name(),
                                                item.categoryName(),
                                                item.latitude(),
                                                item.longitude(),
                                                item.distanceKm(),
                                                item.estimatedDriveMinutes(),
                                                item.recommendationScore(),
                                                item.allTags() == null
                                                        ? item.tags()
                                                        : item.allTags(),
                                                item.recommendationReason()
                                        )
                                )
                );

        return result;
    }

    /*
     * 식당 일정 생성용 후보군.
     *
     * FoodPreference.CAFE는 식당 카테고리 필터에 사용하지 않는다.
     * CAFE는 카페 후보 생성 여부를 결정하는 값으로만 사용한다.
     */
    @Cacheable(
            cacheNames = "tripPlanRestaurantCandidates",
            key = "#cacheKey"
    )
    public List<TripPlanCandidatePool.RestaurantCandidate>
    getRestaurantCandidates(
            String cacheKey,
            Double latitude,
            Double longitude,
            Set<TripPreference> preferences,
            Set<FoodPreference> foodPreferences
    ) {

        RestaurantRecommendResponse response =
                restaurantRecommendationService.recommend(
                        new RestaurantRecommendRequest(
                                latitude,
                                longitude,
                                preferences,
                                foodPreferences,
                                DOMAIN_CANDIDATE_LIMIT
                        )
                );

        ArrayList<TripPlanCandidatePool.RestaurantCandidate> result =
                new ArrayList<>();

        response.restaurants()
                .forEach(
                        item -> {

                            ArrayList<String> matchedPreferences =
                                    new ArrayList<>();

                            if (item.matchedFoodPreferences() != null) {
                                item.matchedFoodPreferences()
                                        .forEach(
                                                preference ->
                                                        matchedPreferences.add(
                                                                preference.name()
                                                        )
                                        );
                            }

                            result.add(
                                    new TripPlanCandidatePool.RestaurantCandidate(
                                            item.id(),
                                            item.restaurantName(),
                                            item.category(),
                                            item.latitude(),
                                            item.longitude(),
                                            item.distanceKm(),
                                            item.estimatedDriveMinutes(),
                                            item.recommendationScore(),
                                            matchedPreferences,
                                            item.summary(),
                                            item.tags(),
                                            item.recommendationReason()
                                    )
                            );
                        }
                );

        return result;
    }

    /*
     * 카페 일정 생성용 후보군.
     *
     * 메인 화면에서 FoodPreference.CAFE가 선택된 경우에만
     * TripPlanCandidateService가 이 메서드를 호출한다.
     */
    @Cacheable(
            cacheNames = "tripPlanCafeCandidates",
            key = "#cacheKey"
    )
    public List<TripPlanCandidatePool.CafeCandidate>
    getCafeCandidates(
            String cacheKey,
            Double latitude,
            Double longitude,
            Set<TripPreference> preferences
    ) {

        CafeRecommendResponse response =
                cafeRecommendationService.recommend(
                        new CafeRecommendRequest(
                                latitude,
                                longitude,
                                preferences,
                                DOMAIN_CANDIDATE_LIMIT
                        )
                );

        ArrayList<TripPlanCandidatePool.CafeCandidate> result =
                new ArrayList<>();

        response.cafes()
                .forEach(
                        item ->
                                result.add(
                                        new TripPlanCandidatePool.CafeCandidate(
                                                item.id(),
                                                item.cafeName(),
                                                item.category(),
                                                item.latitude(),
                                                item.longitude(),
                                                item.distanceKm(),
                                                item.estimatedDriveMinutes(),
                                                item.recommendationScore(),
                                                item.summary(),
                                                item.tags(),
                                                item.recommendationReason()
                                        )
                                )
                );

        return result;
    }
}