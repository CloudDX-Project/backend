package com.travel.accommodation.batch;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public AccommodationBootstrapService.BootstrapResult
    bootstrap() {

        return bootstrapService.bootstrap();
    }
}