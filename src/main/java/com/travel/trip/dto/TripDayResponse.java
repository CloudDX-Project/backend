package com.travel.trip.dto;

import com.travel.trip.entity.TripDay;
import com.travel.trip.plan.dto.TripPlanItemResponse;

import java.time.LocalDate;
import java.util.List;

public record TripDayResponse(

        Long id,

        Integer dayNumber,

        LocalDate date,

        List<TripPlanItemResponse> items

) {

    public static TripDayResponse from(
            TripDay tripDay
    ) {
        return new TripDayResponse(
                tripDay.getId(),
                tripDay.getDayNumber(),
                tripDay.getDate(),
                tripDay.getPlanItems()
                        .stream()
                        .map(TripPlanItemResponse::from)
                        .toList()
        );
    }
}