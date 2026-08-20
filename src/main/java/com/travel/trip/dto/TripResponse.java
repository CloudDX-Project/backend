package com.travel.trip.dto;

import com.travel.trip.entity.Trip;

import java.time.LocalDate;

public record TripResponse(
        Long id,
        String departure,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        int peopleCount,
        Long budget
) {

    public static TripResponse from(Trip trip) {
        return new TripResponse(
                trip.getId(),
                trip.getDeparture(),
                trip.getDestination(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getPeopleCount(),
                trip.getBudget()
        );
    }
}