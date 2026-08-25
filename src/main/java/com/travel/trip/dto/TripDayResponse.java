package com.travel.trip.dto;

import com.travel.trip.entity.TripDay;

import java.time.LocalDate;

public record TripDayResponse(

        Long id,

        Integer dayNumber,

        LocalDate date

) {

    public static TripDayResponse from(
            TripDay tripDay
    ) {

        return new TripDayResponse(
                tripDay.getId(),
                tripDay.getDayNumber(),
                tripDay.getDate()
        );
    }
}