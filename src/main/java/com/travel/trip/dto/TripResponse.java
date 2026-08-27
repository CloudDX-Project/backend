package com.travel.trip.dto;

import com.travel.trip.entity.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record TripResponse(
        Long id,
        String departure,
        String destination,

        LocalDate startDate,
        LocalTime startTime,

        LocalDate endDate,
        LocalTime endTime,

        int peopleCount,

        MainTransportMode mainTransportMode,
        LocalTransportMode localTransportMode,

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
                trip.getStartTime(),

                trip.getEndDate(),
                trip.getEndTime(),

                trip.getPeopleCount(),

                trip.getMainTransportMode(),
                trip.getLocalTransportMode(),

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