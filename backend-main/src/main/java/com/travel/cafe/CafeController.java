package com.travel.cafe;

import com.travel.cafe.dto.CafeDetailResponse;
import com.travel.cafe.dto.CafeRecommendRequest;
import com.travel.cafe.dto.CafeRecommendResponse;
import com.travel.cafe.dto.CafeSearchRequest;
import com.travel.cafe.dto.CafeSearchResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "카페", description = "카페 상세 정보, 검색 결과 및 여행 조건 기반 추천을 제공합니다.")
@RestController
@RequestMapping({"/api/cafes"})
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


    @GetMapping("/{cafeId}")
    @Operation(summary = "카페 상세 조회", description = "카페 ID로 기본 정보와 대표 메뉴 등 상세 정보를 조회합니다.")
    public CafeDetailResponse getDetail(
            @PathVariable Long cafeId
    ) {
        return cafeService.getDetail(
                cafeId
        );
    }

    @PostMapping("/search")
    @Operation(summary = "카페 검색", description = "지역과 검색 조건에 맞는 카페 후보를 조회합니다.")
    public CafeSearchResponse search(
            @Valid @RequestBody CafeSearchRequest request
    ) {
        return cafeService.search(request);
    }

    @PostMapping("/recommend")
    @Operation(summary = "카페 추천", description = "여행 지역과 사용자 선호도에 적합한 카페를 추천합니다.")
    public CafeRecommendResponse recommend(
            @Valid @RequestBody CafeRecommendRequest request
    ) {
        return cafeRecommendationService.recommend(request);
    }
}
