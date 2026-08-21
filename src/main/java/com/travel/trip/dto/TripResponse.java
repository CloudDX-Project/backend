package com.travel.trip.dto;

import com.travel.trip.entity.TransportType;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripPreference;

import java.time.LocalDate;
import java.util.Set;

public record TripResponse(
        Long id,
        String departure,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        int peopleCount,
        Long budget,
        Long mealBudgetPerPersonPerDay,
        TransportType transportType,
        Set<TripPreference> preferences
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
                trip.getTransportType(),
                Set.copyOf(trip.getPreferences())
        );
    }
}