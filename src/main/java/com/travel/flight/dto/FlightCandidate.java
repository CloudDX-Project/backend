package com.travel.flight.dto;

import com.travel.flight.type.FlightDirection;
import com.travel.flight.type.FlightPriceType;

import java.io.Serializable;
import java.time.LocalDateTime;

public record FlightCandidate(

        String id,

        FlightDirection direction,

        String airline,

        String airlineCode,

        String flightNumber,

        String departureAirport,

        String arrivalAirport,

        LocalDateTime departureTime,

        LocalDateTime arrivalTime,

        int estimatedPricePerPerson,

        int estimatedTotalPrice,

        FlightPriceType priceType,

        String aircraft,

        String status

) implements Serializable {
}