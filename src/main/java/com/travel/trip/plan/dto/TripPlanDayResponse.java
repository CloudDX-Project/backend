package com.travel.trip.plan.dto;

import java.time.LocalDate;
import java.util.List;

public record TripPlanDayResponse(

        Integer dayNumber,

        LocalDate date,

        List<TripPlanItemResponse> items

) {
}