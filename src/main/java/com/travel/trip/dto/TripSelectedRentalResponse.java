package com.travel.trip.dto;

import com.travel.trip.entity.TripRentalSelection;

public record TripSelectedRentalResponse(

        String id,

        String company,

        String car,

        String pickup,

        Double latitude,

        Double longitude,

        Integer estimatedShuttleMinutes

) {

    public static TripSelectedRentalResponse from(
            TripRentalSelection rental
    ) {
        if (rental == null) {
            return null;
        }

        return new TripSelectedRentalResponse(
                rental.getRentalId(),
                rental.getCompany(),
                rental.getCar(),
                rental.getPickup(),
                rental.getLatitude(),
                rental.getLongitude(),
                rental.getEstimatedShuttleMinutes()
        );
    }
}