package com.travel.trip.dto;

import com.travel.trip.entity.SegmentTransportMode;
import com.travel.routing.dto.RoutePoint;
import com.travel.routing.util.RoutePathCodec;
import com.travel.trip.entity.TransportSegment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TransportSegmentResponse(

        Long id,

        Long tripDayId,

        Integer dayNumber,

        LocalDate date,

        Integer sequence,

        SegmentTransportMode mode,

        String departureName,

        String arrivalName,

        Double departureLatitude,

        Double departureLongitude,

        Double arrivalLatitude,

        Double arrivalLongitude,

        LocalDateTime departureAt,

        LocalDateTime arrivalAt,

        Double distanceKm,

        Long durationMinutes,

        Long cost,

        String routeProvider,

        List<RoutePoint> path

) {

    public static TransportSegmentResponse from(
            TransportSegment segment
    ) {

        return new TransportSegmentResponse(
                segment.getId(),

                segment.getTripDay().getId(),

                segment.getTripDay().getDayNumber(),

                segment.getTripDay().getDate(),

                segment.getSequence(),

                segment.getMode(),

                segment.getDepartureName(),

                segment.getArrivalName(),

                segment.getDepartureLatitude(),

                segment.getDepartureLongitude(),

                segment.getArrivalLatitude(),

                segment.getArrivalLongitude(),

                segment.getDepartureAt(),

                segment.getArrivalAt(),

                segment.getDistanceKm(),

                segment.getDurationMinutes(),

                segment.getCost(),

                segment.getRouteProvider(),

                RoutePathCodec.decode(
                        segment.getRoutePathJson()
                )
        );
    }
}