package com.travel.attraction;

import com.travel.attraction.dto.AttractionRecommendRequest;
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
