package com.travel.trip.plan.dto;

import com.travel.flight.dto.FlightCandidate;
import com.travel.trip.entity.LocalTransportMode;
import com.travel.trip.entity.MainTransportMode;
import com.travel.weather.DailyWeatherResponse;

import java.util.List;

public record TripPlanResponse(

        Long tripId,

        String planner,

        String timeBasis,

        MainTransportMode mainTransportMode,

        LocalTransportMode localTransportMode,

        TripPlanSelectedAccommodation selectedAccommodation,

        FlightCandidate outboundFlight,

        FlightCandidate returnFlight,

        List<DailyWeatherResponse> weather,

        int attractionCandidateCount,

        int restaurantCandidateCount,

        int cafeCandidateCount,

        List<TripPlanDayResponse> days

) {
}