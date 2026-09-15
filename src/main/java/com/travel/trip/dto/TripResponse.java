package com.travel.trip.dto;

import com.travel.flight.dto.FlightCandidate;
import com.travel.trip.entity.FoodPreference;
import com.travel.trip.entity.LocalTransportMode;
import com.travel.trip.entity.MainTransportMode;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripPace;
import com.travel.trip.entity.TripPreference;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record TripResponse(

        Long id,

        String departure,

        Double departureLatitude,

        Double departureLongitude,

        String destination,

        Double destinationLatitude,

        Double destinationLongitude,

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

        Set<FoodPreference> foodPreferences,

        TripSelectedAccommodationResponse selectedAccommodation,

        TripSelectedRentalResponse selectedRental,

        FlightCandidate outboundFlight,

        FlightCandidate returnFlight,

        List<TripDayResponse> days

) {

    public static TripResponse from(
            Trip trip
    ) {

        return new TripResponse(
                trip.getId(),

                trip.getDeparture(),
                trip.getDepartureLatitude(),
                trip.getDepartureLongitude(),

                trip.getDestination(),
                trip.getDestinationLatitude(),
                trip.getDestinationLongitude(),

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

                Set.copyOf(
                        trip.getPreferences()
                ),

                Set.copyOf(
                        trip.getFoodPreferences()
                ),

                TripSelectedAccommodationResponse.from(
                        trip.getSelectedAccommodation()
                ),

                TripSelectedRentalResponse.from(
                        trip.getSelectedRental()
                ),

                trip.getOutboundFlightCandidate(),

                trip.getReturnFlightCandidate(),

                trip.getTripDays()
                        .stream()
                        .map(
                                TripDayResponse::from
                        )
                        .toList()
        );
    }
}