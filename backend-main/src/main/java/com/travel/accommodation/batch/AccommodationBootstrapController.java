package com.travel.accommodation.batch;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "숙소 데이터 관리", description = "관리자가 공공 숙소 데이터를 초기 적재하거나 갱신할 때 사용하는 API입니다.")
@RestController
@RequestMapping(
        "/api/admin/accommodations"
)
public class AccommodationBootstrapController {

    private final AccommodationBootstrapService
            bootstrapService;


    public AccommodationBootstrapController(
            AccommodationBootstrapService bootstrapService
    ) {
        this.bootstrapService =
                bootstrapService;
    }


    @PostMapping("/bootstrap")
    @Operation(summary = "숙소 데이터 초기 적재", description = "외부 숙소 원천 데이터를 수집·정규화하여 내부 데이터베이스에 적재합니다. 관리자용 기능입니다.")
    public AccommodationBootstrapService.BootstrapResult
    bootstrap() {

        return bootstrapService.bootstrap();
    }
}
