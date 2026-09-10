package com.travel.cafe;

import com.travel.cafe.dto.CafeRecommendRequest;
import com.travel.cafe.dto.CafeRecommendResponse;
import com.travel.cafe.dto.CafeSearchRequest;
import com.travel.cafe.dto.CafeSearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cafes")
public class CafeController {

    private final CafeService cafeService;

    private final CafeRecommendationService cafeRecommendationService;

    public CafeController(
            CafeService cafeService,
            CafeRecommendationService cafeRecommendationService
    ) {
        this.cafeService = cafeService;
        this.cafeRecommendationService = cafeRecommendationService;
    }

    @PostMapping("/search")
    public CafeSearchResponse search(
            @Valid @RequestBody CafeSearchRequest request
    ) {
        return cafeService.search(request);
    }

    @PostMapping("/recommend")
    public CafeRecommendResponse recommend(
            @Valid @RequestBody CafeRecommendRequest request
    ) {
        return cafeRecommendationService.recommend(request);
    }
}