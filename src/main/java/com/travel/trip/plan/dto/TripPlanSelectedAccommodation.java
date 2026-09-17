package com.travel.trip.plan.dto;

import java.io.Serializable;

public record TripPlanSelectedAccommodation(

        Long accommodationId,
        String providerId,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String checkInTime,
        String checkOutTime

) implements Serializable {
}