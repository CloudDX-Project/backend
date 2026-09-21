package com.travel.flight.dto;

import java.util.List;

public record FlightSearchResponse(

        String departureAirport,

        String arrivalAirport,

        List<FlightCandidate> outboundFlights,

        List<FlightCandidate> returnFlights

) {
}