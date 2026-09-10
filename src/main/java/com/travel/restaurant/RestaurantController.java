package com.travel.restaurant;

import com.travel.restaurant.dto.RestaurantRecommendRequest;
import com.travel.restaurant.dto.RestaurantRecommendResponse;
import com.travel.restaurant.dto.RestaurantSearchRequest;
import com.travel.restaurant.dto.RestaurantSearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
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

    @PostMapping("/search")
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