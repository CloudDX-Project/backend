package com.travel.restaurant;

import com.travel.global.util.RecommendationMath;

import com.travel.restaurant.data.RestaurantData;
import com.travel.restaurant.data.RestaurantMenuData;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.restaurant.dto.RestaurantCandidate;
import com.travel.restaurant.dto.RestaurantDetailResponse;
import com.travel.restaurant.dto.RestaurantSearchRequest;
import com.travel.restaurant.dto.RestaurantSearchResponse;
import com.travel.restaurant.repository.RestaurantRepository;
import com.travel.trip.entity.FoodPreference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RestaurantService {

    /*
     * 실제 TMAP/Kakao Routing 연동 전 임시값.
     */
    private static final double
            ESTIMATED_DRIVE_SPEED_KMH =
            45.0;

    private final RestaurantRepository restaurantRepository;

    public RestaurantService(
            RestaurantRepository restaurantRepository
    ) {
        this.restaurantRepository =
                restaurantRepository;
    }


    @Transactional(readOnly = true)
    public RestaurantDetailResponse getDetail(
            Long restaurantId
    ) {

        RestaurantData restaurant =
                restaurantRepository.findById(
                        restaurantId
                );

        if (restaurant == null) {
            throw new BusinessException(
                    ErrorCode.RESTAURANT_NOT_FOUND
            );
        }

        List<RestaurantMenuData> menus =
                restaurantRepository
                        .findMenusByRestaurantIds(
                                List.of(restaurantId)
                        )
                        .getOrDefault(
                                restaurantId,
                                List.of()
                        );

        return RestaurantDetailResponse.from(
                restaurant,
                menus
        );
    }

    @Transactional(readOnly = true)
    public RestaurantSearchResponse search(
            RestaurantSearchRequest request
    ) {

        Set<FoodPreference> requestedPreferences =
                request.resolvedFoodPreferences();

        List<ScoredRestaurant> sorted =
                restaurantRepository
                        .findAllLocated()
                        .stream()

                        /*
                         * FoodPreference가 있으면
                         * DB category 기준 필터링.
                         */
                        .filter(
                                restaurant ->
                                        matchesRequestedCategory(
                                                restaurant,
                                                requestedPreferences
                                        )
                        )

                        .map(
                                restaurant ->
                                        new ScoredRestaurant(
                                                restaurant,
                                                RecommendationMath.distanceKm(
                                                        request.originLatitude(),
                                                        request.originLongitude(),
                                                        restaurant.latitude(),
                                                        restaurant.longitude()
                                                )
                                        )
                        )

                        .sorted(
                                Comparator
                                        .comparingDouble(
                                                ScoredRestaurant::distanceKm
                                        )
                        )

                        .limit(
                                request.resolvedLimit()
                        )

                        .toList();

        List<Long> ids =
                sorted.stream()
                        .map(
                                item ->
                                        item.restaurant().id()
                        )
                        .toList();

        Map<Long, List<RestaurantMenuData>> menuMap =
                restaurantRepository
                        .findMenusByRestaurantIds(
                                ids
                        );

        List<RestaurantCandidate> restaurants =
                sorted.stream()
                        .map(
                                item ->
                                        toCandidate(
                                                item,
                                                requestedPreferences,
                                                menuMap.getOrDefault(
                                                        item.restaurant().id(),
                                                        List.of()
                                                )
                                        )
                        )
                        .toList();

        return new RestaurantSearchResponse(

                request.originLatitude(),

                request.originLongitude(),

                requestedPreferences,

                restaurants.size(),

                "STRAIGHT_LINE_ESTIMATE",

                restaurants
        );
    }

    private boolean matchesRequestedCategory(
            RestaurantData restaurant,
            Set<FoodPreference> preferences
    ) {

        if (preferences == null
                || preferences.isEmpty()) {

            return true;
        }

        return preferences
                .stream()
                .anyMatch(
                        preference ->
                                preference.matchesCategory(
                                        restaurant.category()
                                )
                );
    }

    private RestaurantCandidate toCandidate(
            ScoredRestaurant scored,
            Set<FoodPreference> requestedPreferences,
            List<RestaurantMenuData> menus
    ) {

        RestaurantData restaurant =
                scored.restaurant();

        return new RestaurantCandidate(

                restaurant.id(),

                restaurant.kakaoPlaceId(),

                restaurant.restaurantName(),

                restaurant.category(),

                resolveMatchedPreferences(
                        restaurant.category(),
                        requestedPreferences
                ),

                restaurant.phone(),

                restaurant.address(),

                restaurant.roadAddress(),

                restaurant.latitude(),

                restaurant.longitude(),

                restaurant.placeUrl(),

                restaurant.rating(),

                restaurant.reviewCount(),

                restaurant.businessHours(),

                restaurant.summary(),

                restaurant.tags(),

                restaurant.facilities(),

                restaurant.lastScrapedAt(),

                RecommendationMath.round(
                        scored.distanceKm(),
                        2
                ),

                calculateEstimatedDriveMinutes(
                        scored.distanceKm()
                ),

                menus
        );
    }

    private Set<FoodPreference>
    resolveMatchedPreferences(
            String category,
            Set<FoodPreference> requestedPreferences
    ) {

        if (requestedPreferences != null
                && !requestedPreferences.isEmpty()) {

            return requestedPreferences
                    .stream()
                    .filter(
                            preference ->
                                    preference.matchesCategory(
                                            category
                                    )
                    )
                    .collect(
                            Collectors.toUnmodifiableSet()
                    );
        }

        return Arrays
                .stream(
                        FoodPreference.values()
                )
                .filter(
                        preference ->
                                preference.matchesCategory(
                                        category
                                )
                )
                .collect(
                        Collectors.toUnmodifiableSet()
                );
    }

    private int calculateEstimatedDriveMinutes(
            double distanceKm
    ) {

        return (int) Math.ceil(
                (
                        distanceKm
                                / ESTIMATED_DRIVE_SPEED_KMH
                )
                        * 60.0
        );
    }

    private record ScoredRestaurant(

            RestaurantData restaurant,

            double distanceKm

    ) {
    }
}
