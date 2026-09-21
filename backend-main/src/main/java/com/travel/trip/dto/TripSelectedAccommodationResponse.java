package com.travel.trip.dto;

import com.travel.trip.entity.TripAccommodationSelection;

public record TripSelectedAccommodationResponse(

        Long accommodationId,
        String providerId,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String checkInTime,
        String checkOutTime

) {

    public static TripSelectedAccommodationResponse from(
            TripAccommodationSelection accommodation
    ) {
        if (accommodation == null) {
            return null;
        }

        return new TripSelectedAccommodationResponse(
                accommodation.getAccommodationId(),
                accommodation.getProviderId(),
                accommodation.getName(),
                accommodation.getAddress(),
                accommodation.getLatitude(),
                accommodation.getLongitude(),
                accommodation.getCheckInTime(),
                accommodation.getCheckOutTime()
        );
    }
}