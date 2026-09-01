package com.travel.flight;

import java.util.List;

public record FlightSearchResponse(

        String departureAirport,

        String arrivalAirport,

        List<FlightCandidate> outboundFlights,

        List<FlightCandidate> returnFlights

) {
}