package com.travel.accommodation;

import com.travel.accommodation.dto.AccommodationRecommendRequest;
import com.travel.accommodation.dto.AccommodationRecommendResponse;
import com.travel.accommodation.dto.AccommodationSearchRequest;
import com.travel.accommodation.dto.AccommodationSearchResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "숙소", description = "여행 지역의 숙소를 검색하고 사용자 조건에 맞는 숙소를 추천합니다.")
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
    @Operation(summary = "숙소 검색", description = "지역, 날짜, 인원, 가격 조건을 기준으로 이용 가능한 숙소 후보를 조회합니다.")
    public AccommodationSearchResponse search(
            @Valid
            @RequestBody
            AccommodationSearchRequest request
    ) {
        return accommodationService.search(request);
    }

    @PostMapping("/recommend")
    @Operation(summary = "숙소 추천", description = "검색된 숙소 후보 중 여행 조건과 선호도에 적합한 숙소를 추천합니다.")
    public AccommodationRecommendResponse recommend(
            @Valid
            @RequestBody
            AccommodationRecommendRequest request
    ) {
        return recommendationService.recommend(request);
    }
}
