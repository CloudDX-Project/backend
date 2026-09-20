package com.travel.trip.controller;

import com.travel.global.response.ApiResponse;
import com.travel.trip.dto.TransportSegmentCreateRequest;
import com.travel.trip.dto.TransportSegmentReorderRequest;
import com.travel.trip.dto.TransportSegmentResponse;
import com.travel.trip.dto.TransportSegmentUpdateRequest;
import com.travel.trip.service.TransportSegmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "일정 이동 구간", description = "여행 일차별 장소 사이 이동 구간을 생성·조회·수정·정렬·삭제합니다.")
@RestController
@RequestMapping(
        "/api/trips/{tripId}/days/{dayId}/transport-segments"
)
@RequiredArgsConstructor
public class TransportSegmentController {

    private final TransportSegmentService
            transportSegmentService;

    @PostMapping
    @Operation(summary = "이동 구간 생성", description = "해당 일차에 출발지와 도착지 사이의 이동수단, 시간, 비용 및 경로 정보를 추가합니다.")
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
    @Operation(summary = "이동 구간 목록 조회", description = "해당 여행 일차에 저장된 모든 이동 구간을 순서대로 조회합니다.")
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
    @Operation(summary = "이동 구간 수정", description = "선택한 이동 구간의 이동수단, 시간, 비용 또는 경로 정보를 수정합니다.")
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
    @Operation(summary = "이동 구간 순서 변경", description = "해당 일차의 이동 구간 순서를 요청한 순서대로 재배치합니다.")
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
    @Operation(summary = "이동 구간 삭제", description = "선택한 이동 구간을 여행 일정에서 삭제합니다.")
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
