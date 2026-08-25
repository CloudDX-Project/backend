package com.travel.trip.service;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.dto.TransportSegmentCreateRequest;
import com.travel.trip.dto.TransportSegmentResponse;
import com.travel.trip.entity.TransportSegment;
import com.travel.trip.entity.TripDay;
import com.travel.trip.repository.TransportSegmentRepository;
import com.travel.trip.repository.TripDayRepository;
import com.travel.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransportSegmentService {

    private final TripRepository tripRepository;

    private final TripDayRepository tripDayRepository;

    private final TransportSegmentRepository
            transportSegmentRepository;

    @Transactional
    public TransportSegmentResponse createSegment(
            Long userId,
            Long tripId,
            Long dayId,
            TransportSegmentCreateRequest request
    ) {

        getOwnedTrip(
                userId,
                tripId
        );

        TripDay tripDay =
                getTripDay(
                        tripId,
                        dayId
                );

        validateSegmentDate(
                tripDay,
                request
        );

        TransportSegment segment =
                TransportSegment.builder()
                        .tripDay(tripDay)
                        .sequence(request.sequence())
                        .mode(request.mode())
                        .departureName(
                                request.departureName()
                        )
                        .arrivalName(
                                request.arrivalName()
                        )
                        .departureLatitude(
                                request.departureLatitude()
                        )
                        .departureLongitude(
                                request.departureLongitude()
                        )
                        .arrivalLatitude(
                                request.arrivalLatitude()
                        )
                        .arrivalLongitude(
                                request.arrivalLongitude()
                        )
                        .departureAt(
                                request.departureAt()
                        )
                        .arrivalAt(
                                request.arrivalAt()
                        )
                        .cost(0L)
                        .build();

        tripDay.addTransportSegment(
                segment
        );

        TransportSegment savedSegment =
                transportSegmentRepository
                        .save(segment);

        return TransportSegmentResponse.from(
                savedSegment
        );
    }

    public List<TransportSegmentResponse>
    getSegments(
            Long userId,
            Long tripId,
            Long dayId
    ) {

        getOwnedTrip(
                userId,
                tripId
        );

        getTripDay(
                tripId,
                dayId
        );

        return transportSegmentRepository
                .findAllByTripDayIdOrderBySequenceAsc(
                        dayId
                )
                .stream()
                .map(
                        TransportSegmentResponse::from
                )
                .toList();
    }

    @Transactional
    public void deleteSegment(
            Long userId,
            Long tripId,
            Long dayId,
            Long segmentId
    ) {

        getOwnedTrip(
                userId,
                tripId
        );

        TripDay tripDay =
                getTripDay(
                        tripId,
                        dayId
                );

        TransportSegment segment =
                transportSegmentRepository
                        .findByIdAndTripDayId(
                                segmentId,
                                dayId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.TRANSPORT_SEGMENT_NOT_FOUND
                                )
                        );

        tripDay.removeTransportSegment(
                segment
        );

        transportSegmentRepository.delete(
                segment
        );
    }

    private void validateSegmentDate(
            TripDay tripDay,
            TransportSegmentCreateRequest request
    ) {

        if (
                request.departureAt() != null
                        &&
                        !request.departureAt()
                                .toLocalDate()
                                .equals(
                                        tripDay.getDate()
                                )
        ) {

            throw new BusinessException(
                    ErrorCode.INVALID_TRANSPORT_SEGMENT_DATE
            );
        }

        if (
                request.departureAt() != null
                        &&
                        request.arrivalAt() != null
                        &&
                        request.arrivalAt()
                                .isBefore(
                                        request.departureAt()
                                )
        ) {

            throw new BusinessException(
                    ErrorCode.INVALID_TRANSPORT_SEGMENT_TIME
            );
        }
    }

    private void getOwnedTrip(
            Long userId,
            Long tripId
    ) {

        tripRepository
                .findByIdAndUserId(
                        tripId,
                        userId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.TRIP_NOT_FOUND
                        )
                );
    }

    private TripDay getTripDay(
            Long tripId,
            Long dayId
    ) {

        return tripDayRepository
                .findByIdAndTripId(
                        dayId,
                        tripId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.TRIP_DAY_NOT_FOUND
                        )
                );
    }
}