package com.travel.accommodation;

import com.travel.accommodation.dto.AccommodationRecommendRequest;
import com.travel.accommodation.dto.AccommodationRecommendResponse;
import com.travel.accommodation.dto.AccommodationSearchRequest;
import com.travel.accommodation.dto.AccommodationSearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accommodations")
public class AccommodationController {

    private final AccommodationService accommodationService;
    private final AccommodationRecommendationService recommendationService;

    public AccommodationController(
            AccommodationService accommodationService,
            AccommodationRecommendationService recommendationService
    ) {
        this.accommodationService = accommodationService;
        this.recommendationService = recommendationService;
    }

    @PostMapping("/search")
    public AccommodationSearchResponse search(
            @Valid
            @RequestBody
            AccommodationSearchRequest request
    ) {
        return accommodationService.search(request);
    }

    @PostMapping("/recommend")
    public AccommodationRecommendResponse recommend(
            @Valid
            @RequestBody
            AccommodationRecommendRequest request
    ) {
        return recommendationService.recommend(request);
    }
}