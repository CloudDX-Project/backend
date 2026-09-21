package com.travel.trip.plan.edit;

import com.travel.trip.plan.type.TripPlanItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** 장소명, 좌표, 날짜와 항공편 시각은 위·변조 방지를 위해 클라이언트 입력으로 받지 않는다. */
@Schema(description = "변경된 일정 저장 요청. 서버가 발급한 키와 장소 ID만 전송하며 장소명·좌표는 서버에서 다시 조회합니다.")
public record PlanEditRequest(
        @Schema(description = "편집을 시작할 때 조회한 일정 revision", example = "3")
        @NotNull @PositiveOrZero Long baseRevision,
        @Schema(description = "일정 생성 작업의 고유 요청 ID", example = "3820de93-ad1f-4704-83ee-8b39aa0f578f")
        @NotBlank @Size(max = 36) String requestId,
        @Schema(description = "일차별 변경 일정")
        @NotEmpty @Size(max = 31) List<@NotNull @Valid Day> days
) {
    @Schema(description = "하루 일정 변경 내용")
    public record Day(
            @Schema(description = "여행 일차", example = "1")
            @NotNull @Min(1) @Max(31) Integer dayNumber,
            @Schema(description = "해당 일차의 일정 항목 순서")
            @NotEmpty @Size(max = 30) List<@NotNull @Valid Item> items
    ) {}

    @Schema(description = "개별 일정 항목 변경 내용")
    public record Item(
            @Schema(description = "서버가 발급한 일차:순서 형식의 일정 키", example = "1:4")
            @Pattern(regexp = "[1-9][0-9]*:[1-9][0-9]*") String itemKey,
            @Schema(description = "장소 체류시간(분)", example = "90")
            @Min(10) @Max(240) Integer stayMinutes,
            @Schema(description = "사용자가 지정한 시작 시각. 순서 변경 시 null을 권장합니다.", example = "14:30")
            @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String startTime,
            @Schema(description = "장소를 교체하지 않으면 null, 교체하면 새 장소 식별자를 입력합니다.")
            @Valid Place replacement
    ) {}

    @Schema(description = "교체할 장소 식별자")
    public record Place(
            @Schema(description = "교체 장소 종류", allowableValues = {"ATTRACTION", "RESTAURANT", "CAFE"})
            @NotNull TripPlanItemType type,
            @Schema(description = "내부 장소 ID", example = "77")
            @NotNull @Positive Long placeId
    ) {}
}
