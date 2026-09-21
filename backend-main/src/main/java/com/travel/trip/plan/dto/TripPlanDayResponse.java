package com.travel.trip.plan.dto;

import com.travel.trip.dto.TransportSegmentResponse;

import java.time.LocalDate;
import java.util.List;

public record TripPlanDayResponse(

        Integer dayNumber,

        LocalDate date,

        List<TripPlanItemResponse> items,

        List<TransportSegmentResponse> transportSegments

) {
}