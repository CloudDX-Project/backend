package com.travel.trip.dto;

import com.travel.trip.entity.TransportType;
import com.travel.trip.entity.Trip;

import java.time.LocalDate;

public record TripResponse(
        Long id,
        String departure,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        int peopleCount,
        Long budget,
        Long mealBudgetPerPersonPerDay,
        TransportType transportType
) {

    public static TripResponse from(Trip trip) {
        return new TripResponse(
                trip.getId(),
                trip.getDeparture(),
                trip.getDestination(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getPeopleCount(),
                trip.getBudget(),
                trip.getMealBudgetPerPersonPerDay(),
                trip.getTransportType()
        );
    }
}