package com.travel.accommodation;

import com.travel.accommodation.dto.AccommodationSearchRequest;
import com.travel.accommodation.dto.AccommodationSearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accommodations")
public class AccommodationController {

    private final AccommodationService accommodationService;


    public AccommodationController(
            AccommodationService accommodationService
    ) {
        this.accommodationService =
                accommodationService;
    }


    @PostMapping("/search")
    public AccommodationSearchResponse search(
            @Valid
            @RequestBody
            AccommodationSearchRequest request
    ) {

        return accommodationService.search(
                request
        );
    }
}