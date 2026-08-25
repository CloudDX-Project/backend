package com.travel.trip.dto;

import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripPreference;
import com.travel.trip.entity.TripPace;

import java.time.LocalDate;
import java.util.List;
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
        TripPace pace,
        Set<TripPreference> preferences,
        List<TripDayResponse> days
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
                trip.getPace(),
                Set.copyOf(trip.getPreferences()),
                trip.getTripDays()
                        .stream()
                        .map(TripDayResponse::from)
                        .toList()
        );
    }
}