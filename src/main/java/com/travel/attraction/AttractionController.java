package com.travel.attraction;

import com.travel.attraction.dto.AttractionRecommendRequest;
import com.travel.attraction.dto.AttractionRecommendResponse;
import com.travel.attraction.dto.AttractionSearchRequest;
import com.travel.attraction.dto.AttractionSearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attractions")
public class AttractionController {

    private final AttractionService attractionService;

    private final AttractionRecommendationService
            attractionRecommendationService;


    public AttractionController(
            AttractionService attractionService,
            AttractionRecommendationService attractionRecommendationService
    ) {

        this.attractionService =
                attractionService;

        this.attractionRecommendationService =
                attractionRecommendationService;
    }


    @PostMapping("/search")
    public AttractionSearchResponse search(
            @Valid
            @RequestBody
            AttractionSearchRequest request
    ) {

        return attractionService.search(
                request
        );
    }


    @PostMapping("/recommend")
    public AttractionRecommendResponse recommend(
            @Valid
            @RequestBody
            AttractionRecommendRequest request
    ) {

        return attractionRecommendationService
                .recommend(
                        request
                );
    }
}