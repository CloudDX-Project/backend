package com.travel.restaurant;

import com.travel.restaurant.dto.RestaurantDetailResponse;
import com.travel.restaurant.dto.RestaurantRecommendRequest;
import com.travel.restaurant.dto.RestaurantRecommendResponse;
import com.travel.restaurant.dto.RestaurantSearchRequest;
import com.travel.restaurant.dto.RestaurantSearchResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "음식점", description = "음식점 상세 정보, 검색 결과 및 음식 취향 기반 추천을 제공합니다.")
@RestController
@RequestMapping({"/api/restaurants"})
public class RestaurantController {

    private final RestaurantService restaurantService;

    private final RestaurantRecommendationService
            restaurantRecommendationService;

    public RestaurantController(
            RestaurantService restaurantService,
            RestaurantRecommendationService
                    restaurantRecommendationService
    ) {

        this.restaurantService =
                restaurantService;

        this.restaurantRecommendationService =
                restaurantRecommendationService;
    }


    @GetMapping("/{restaurantId}")
    @Operation(summary = "음식점 상세 조회", description = "음식점 ID로 기본 정보와 대표 메뉴 등 상세 정보를 조회합니다.")
    public RestaurantDetailResponse getDetail(
            @PathVariable Long restaurantId
    ) {
        return restaurantService.getDetail(
                restaurantId
        );
    }

    @PostMapping("/search")
    @Operation(summary = "음식점 검색", description = "지역, 음식 종류 및 검색 조건에 맞는 음식점 후보를 조회합니다.")
    public RestaurantSearchResponse search(
            @Valid
            @RequestBody
            RestaurantSearchRequest request
    ) {

        return restaurantService
                .search(
                        request
                );
    }

    @PostMapping("/recommend")
    @Operation(summary = "음식점 추천", description = "여행 지역과 사용자의 음식 선호도에 적합한 음식점을 추천합니다.")
    public RestaurantRecommendResponse recommend(
            @Valid
            @RequestBody
            RestaurantRecommendRequest request
    ) {

        return restaurantRecommendationService
                .recommend(
                        request
                );
    }
}
