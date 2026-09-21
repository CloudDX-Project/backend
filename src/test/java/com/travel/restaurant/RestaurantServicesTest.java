package com.travel.restaurant;

import com.travel.external.bedrock.BedrockClient;
import com.travel.restaurant.data.RestaurantData;
import com.travel.restaurant.dto.RestaurantRecommendRequest;
import com.travel.restaurant.dto.RestaurantSearchRequest;
import com.travel.restaurant.repository.RestaurantRepository;
import com.travel.trip.entity.FoodPreference;
import com.travel.trip.entity.TripPreference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestaurantServicesTest {

    @Test
    void recommendationScoresMatchingRestaurantWithBedrock() {
        RestaurantRepository repository = mock(RestaurantRepository.class);
        RestaurantData recommendedRestaurant =
                restaurant(1L, "애월 흑돼지", 33.46, 126.31);
        when(repository.findAllLocated()).thenReturn(List.of(recommendedRestaurant));
        when(repository.findMenusByRestaurantIds(anyList())).thenReturn(Map.of());
        BedrockClient bedrockClient = mock(BedrockClient.class);
        when(bedrockClient.converse(anyString())).thenReturn(
                "{\"recommendations\":[{\"restaurantId\":1,\"aiScore\":95,\"reason\":\"제주 향토 메뉴가 좋습니다.\"}]}"
        );
        RestaurantRecommendationService service = new RestaurantRecommendationService(
                repository, bedrockClient, JsonMapper.builder().findAndAddModules().build());
        ReflectionTestUtils.setField(service, "bedrockRerankEnabled", true);

        var response = service.recommend(new RestaurantRecommendRequest(
                33.45,
                126.30,
                Set.of(TripPreference.FOOD),
                Set.of(FoodPreference.KOREAN),
                1
        ));

        assertThat(response.restaurants()).hasSize(1);
        assertThat(response.restaurants().getFirst().restaurantName()).isEqualTo("애월 흑돼지");
        assertThat(response.restaurants().getFirst().recommendationScore()).isBetween(0.0, 100.0);
    }

    @Test
    void searchCalculatesDistanceAndMapsRestaurant() {
        RestaurantRepository repository = mock(RestaurantRepository.class);
        RestaurantData nearbyRestaurant =
                restaurant(2L, "제주 한식", 33.451, 126.301);
        when(repository.findAllLocated()).thenReturn(List.of(nearbyRestaurant));
        when(repository.findMenusByRestaurantIds(anyList())).thenReturn(Map.of());

        var response = new RestaurantService(repository).search(
                new RestaurantSearchRequest(
                        33.45, 126.30, Set.of(FoodPreference.KOREAN), 5));

        assertThat(response.restaurants()).hasSize(1);
        assertThat(response.restaurants().getFirst().distanceKm()).isNotNegative();
    }

    @Test
    void recommendationPrefersRestaurantsWithReliableReviewDataWhenEnoughExist() {
        RestaurantRepository repository = mock(RestaurantRepository.class);
        RestaurantData unknown = restaurant(10L, "정보 없는 식당", 33.4501, 126.3001);
        when(unknown.rating()).thenReturn(null);
        when(unknown.reviewCount()).thenReturn(0);

        List<RestaurantData> trusted = java.util.stream.LongStream.rangeClosed(20, 27)
                .mapToObj(id -> restaurant(id, "제주 대표 맛집 " + id, 33.45, 126.30))
                .toList();
        java.util.ArrayList<RestaurantData> all = new java.util.ArrayList<>(trusted);
        all.add(unknown);
        when(repository.findAllLocated()).thenReturn(all);
        when(repository.findMenusByRestaurantIds(anyList())).thenReturn(Map.of());

        RestaurantRecommendationService service = new RestaurantRecommendationService(
                repository, mock(BedrockClient.class), JsonMapper.builder().build());
        var response = service.recommend(new RestaurantRecommendRequest(
                33.45, 126.30, Set.of(TripPreference.FOOD),
                Set.of(FoodPreference.KOREAN), 8));

        assertThat(response.restaurants()).extracting("restaurantName")
                .doesNotContain("정보 없는 식당");
    }

    private RestaurantData restaurant(Long id, String name, double latitude, double longitude) {
        RestaurantData restaurant = mock(RestaurantData.class);
        when(restaurant.id()).thenReturn(id);
        when(restaurant.restaurantName()).thenReturn(name);
        when(restaurant.category()).thenReturn("음식점 > 한식");
        when(restaurant.latitude()).thenReturn(latitude);
        when(restaurant.longitude()).thenReturn(longitude);
        when(restaurant.rating()).thenReturn(4.8);
        when(restaurant.reviewCount()).thenReturn(800);
        when(restaurant.summary()).thenReturn("제주 향토음식 전문점");
        when(restaurant.tags()).thenReturn("흑돼지 고기국수");
        return restaurant;
    }
}
