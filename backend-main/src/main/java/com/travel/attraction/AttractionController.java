package com.travel.attraction;

import com.travel.attraction.dto.AttractionRecommendRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.travel.attraction.dto.AttractionDetailResponse;
import com.travel.global.response.ApiResponse;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.travel.attraction.dto.AttractionRecommendResponse;
import com.travel.attraction.dto.AttractionSearchRequest;
import com.travel.attraction.dto.AttractionSearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관광지", description = "관광지 상세 정보, 검색 결과 및 여행 취향 기반 추천을 제공합니다.")
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


    @GetMapping("/{attractionsId}")
    @Operation(summary = "관광지 상세 조회", description = "관광지 ID로 명칭, 주소, 소개, 연락처, 좌표 및 대표 이미지 정보를 조회합니다.")
    public ApiResponse<AttractionDetailResponse> getDetail(
            @PathVariable("attractionsId") String attractionsId
    ) {
        try {
            return ApiResponse.success(attractionService.getDetail(Long.parseLong(attractionsId)));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @PostMapping("/search")
    @Operation(summary = "관광지 검색", description = "지역과 검색 조건에 맞는 관광지 후보를 조회합니다.")
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
    @Operation(summary = "관광지 추천", description = "여행 지역, 취향 및 일정 조건을 반영해 관광지를 추천합니다.")
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
