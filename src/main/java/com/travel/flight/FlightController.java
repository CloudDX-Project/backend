package com.travel.flight;

import com.travel.flight.dto.FlightSearchRequest;
import com.travel.flight.dto.FlightSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "항공편", description = "출발·도착 공항과 여행 날짜를 기준으로 실제 운항편 및 예상 운임을 조회합니다.")
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
    @Operation(summary = "항공편 검색", description = "출발지, 목적지, 탑승 날짜 조건으로 항공편 시간표와 예상 가격을 검색합니다.")
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
