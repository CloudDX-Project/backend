package com.travel.flight;

import com.travel.flight.dto.FlightSearchRequest;
import com.travel.flight.dto.FlightSearchResponse;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping(
        "/api/flights"
)
public class FlightController {

    private final FlightService flightService;


    public FlightController(
            FlightService flightService
    ) {

        this.flightService =
                flightService;
    }


    @PostMapping(
            "/search"
    )
    public FlightSearchResponse searchFlights(

            @Valid
            @RequestBody
            FlightSearchRequest request
    ) {

        return flightService.search(
                request
        );
    }
}