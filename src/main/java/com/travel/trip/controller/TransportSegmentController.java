package com.travel.trip.controller;

import com.travel.global.response.ApiResponse;
import com.travel.trip.dto.TransportSegmentCreateRequest;
import com.travel.trip.dto.TransportSegmentReorderRequest;
import com.travel.trip.dto.TransportSegmentResponse;
import com.travel.trip.dto.TransportSegmentUpdateRequest;
import com.travel.trip.service.TransportSegmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/trips/{tripId}/days/{dayId}/transport-segments"
)
@RequiredArgsConstructor
public class TransportSegmentController {

    private final TransportSegmentService
            transportSegmentService;

    @PostMapping
    public ApiResponse<TransportSegmentResponse>
    createSegment(
            Authentication authentication,

            @PathVariable
            Long tripId,

            @PathVariable
            Long dayId,

            @Valid
            @RequestBody
            TransportSegmentCreateRequest request
    ) {

        Long userId =
                (Long) authentication.getPrincipal();

        return ApiResponse.success(
                "이동 구간이 생성되었습니다.",

                transportSegmentService
                        .createSegment(
                                userId,
                                tripId,
                                dayId,
                                request
                        )
        );
    }

    @GetMapping
    public ApiResponse<
            List<TransportSegmentResponse>
            >
    getSegments(
            Authentication authentication,

            @PathVariable
            Long tripId,

            @PathVariable
            Long dayId
    ) {

        Long userId =
                (Long) authentication.getPrincipal();

        return ApiResponse.success(

                transportSegmentService
                        .getSegments(
                                userId,
                                tripId,
                                dayId
                        )
        );
    }

    @PatchMapping("/{segmentId}")
    public ApiResponse<TransportSegmentResponse>
    updateSegment(
            Authentication authentication,
            @PathVariable Long tripId,
            @PathVariable Long dayId,
            @PathVariable Long segmentId,
            @Valid
            @RequestBody
            TransportSegmentUpdateRequest request
    ) {
        Long userId =
                (Long) authentication.getPrincipal();

        return ApiResponse.success(
                "이동 구간이 수정되었습니다.",

                transportSegmentService
                        .updateSegment(
                                userId,
                                tripId,
                                dayId,
                                segmentId,
                                request
                        )
        );
    }

    @PatchMapping("/reorder")
    public ApiResponse<
            List<TransportSegmentResponse>
            >
    reorderSegments(
            Authentication authentication,
            @PathVariable Long tripId,
            @PathVariable Long dayId,
            @Valid
            @RequestBody
            TransportSegmentReorderRequest request
    ) {
        Long userId =
                (Long) authentication.getPrincipal();

        return ApiResponse.success(
                "이동 구간 순서가 변경되었습니다.",

                transportSegmentService
                        .reorderSegments(
                                userId,
                                tripId,
                                dayId,
                                request
                        )
        );
    }

    @DeleteMapping("/{segmentId}")
    public ApiResponse<Void>
    deleteSegment(
            Authentication authentication,

            @PathVariable
            Long tripId,

            @PathVariable
            Long dayId,

            @PathVariable
            Long segmentId
    ) {

        Long userId =
                (Long) authentication.getPrincipal();

        transportSegmentService
                .deleteSegment(
                        userId,
                        tripId,
                        dayId,
                        segmentId
                );

        return ApiResponse.success(
                "이동 구간이 삭제되었습니다."
        );
    }
}