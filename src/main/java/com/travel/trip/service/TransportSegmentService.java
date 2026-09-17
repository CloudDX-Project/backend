package com.travel.trip.service;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.dto.TransportSegmentCreateRequest;
import com.travel.trip.dto.TransportSegmentReorderRequest;
import com.travel.trip.dto.TransportSegmentResponse;
import com.travel.trip.dto.TransportSegmentUpdateRequest;
import com.travel.trip.entity.TransportSegment;
import com.travel.trip.entity.TripDay;
import com.travel.trip.repository.TransportSegmentRepository;
import com.travel.trip.repository.TripDayRepository;
import com.travel.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransportSegmentService {

    private static final int REORDER_TEMP_OFFSET =
            1_000_000;

    private final TripRepository
            tripRepository;

    private final TripDayRepository
            tripDayRepository;

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
                request.departureAt(),
                request.arrivalAt()
        );

        int nextSequence =
                getNextSequence(dayId);

        TransportSegment segment =
                TransportSegment.builder()
                        .tripDay(tripDay)
                        .sequence(nextSequence)
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
    public TransportSegmentResponse updateSegment(
            Long userId,
            Long tripId,
            Long dayId,
            Long segmentId,
            TransportSegmentUpdateRequest request
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
                getTransportSegment(
                        dayId,
                        segmentId
                );

        validateSegmentDate(
                tripDay,
                request.departureAt(),
                request.arrivalAt()
        );

        segment.updateDetails(
                request.mode(),
                request.departureName(),
                request.arrivalName(),
                request.departureLatitude(),
                request.departureLongitude(),
                request.arrivalLatitude(),
                request.arrivalLongitude(),
                request.departureAt(),
                request.arrivalAt()
        );

        return TransportSegmentResponse.from(
                segment
        );
    }

    @Transactional
    public List<TransportSegmentResponse>
    reorderSegments(
            Long userId,
            Long tripId,
            Long dayId,
            TransportSegmentReorderRequest request
    ) {
        getOwnedTrip(
                userId,
                tripId
        );

        getTripDay(
                tripId,
                dayId
        );

        List<TransportSegment> segments =
                transportSegmentRepository
                        .findAllByTripDayIdOrderBySequenceAsc(
                                dayId
                        );

        validateReorderRequest(
                segments,
                request.segmentIds()
        );

        Map<Long, TransportSegment> segmentById =
                new HashMap<>();

        for (
                TransportSegment segment
                : segments
        ) {
            segmentById.put(
                    segment.getId(),
                    segment
            );
        }

        /*
         * DB의
         *
         * (trip_day_id, segment_order)
         *
         * UNIQUE 제약 충돌을 방지하기 위해
         * 먼저 기존 sequence를 임시 값으로 변경.
         */
        for (
                int i = 0;
                i < segments.size();
                i++
        ) {

            segments
                    .get(i)
                    .changeSequence(
                            REORDER_TEMP_OFFSET
                                    + i
                                    + 1
                    );
        }

        transportSegmentRepository.flush();

        /*
         * 프론트 또는 AI가 전달한 ID 순서에 따라
         *
         * sequence = 1 ~ N
         *
         * 을 다시 부여.
         */
        for (
                int i = 0;
                i < request.segmentIds().size();
                i++
        ) {

            Long segmentId =
                    request.segmentIds()
                            .get(i);

            segmentById
                    .get(segmentId)
                    .changeSequence(
                            i + 1
                    );
        }

        transportSegmentRepository.flush();

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
                getTransportSegment(
                        dayId,
                        segmentId
                );

        tripDay.removeTransportSegment(
                segment
        );

        transportSegmentRepository.delete(
                segment
        );

        transportSegmentRepository.flush();

        normalizeSequences(
                dayId
        );
    }

    private int getNextSequence(
            Long dayId
    ) {
        return transportSegmentRepository
                .findTopByTripDayIdOrderBySequenceDesc(
                        dayId
                )
                .map(
                        TransportSegment::getSequence
                )
                .map(
                        sequence ->
                                sequence + 1
                )
                .orElse(1);
    }

    private void normalizeSequences(
            Long dayId
    ) {
        List<TransportSegment> segments =
                transportSegmentRepository
                        .findAllByTripDayIdOrderBySequenceAsc(
                                dayId
                        );

        for (
                int i = 0;
                i < segments.size();
                i++
        ) {

            int expectedSequence =
                    i + 1;

            TransportSegment segment =
                    segments.get(i);

            if (
                    !segment
                            .getSequence()
                            .equals(
                                    expectedSequence
                            )
            ) {
                segment.changeSequence(
                        expectedSequence
                );
            }
        }
    }

    private void validateReorderRequest(
            List<TransportSegment> segments,
            List<Long> requestedIds
    ) {

        if (
                segments.size()
                        != requestedIds.size()
        ) {
            throw new BusinessException(
                    ErrorCode
                            .INVALID_TRANSPORT_SEGMENT_ORDER
            );
        }

        Set<Long> currentIds =
                new HashSet<>();

        for (
                TransportSegment segment
                : segments
        ) {
            currentIds.add(
                    segment.getId()
            );
        }

        Set<Long> requestedIdSet =
                new HashSet<>(
                        requestedIds
                );

        if (
                requestedIdSet.size()
                        != requestedIds.size()

                        ||

                        !currentIds.equals(
                                requestedIdSet
                        )
        ) {

            throw new BusinessException(
                    ErrorCode
                            .INVALID_TRANSPORT_SEGMENT_ORDER
            );
        }
    }

    private void validateSegmentDate(
            TripDay tripDay,
            LocalDateTime departureAt,
            LocalDateTime arrivalAt
    ) {

        if (
                departureAt != null

                        &&

                        !departureAt
                                .toLocalDate()
                                .equals(
                                        tripDay.getDate()
                                )
        ) {

            throw new BusinessException(
                    ErrorCode
                            .INVALID_TRANSPORT_SEGMENT_DATE
            );
        }

        if (
                departureAt != null

                        &&

                        arrivalAt != null

                        &&

                        arrivalAt.isBefore(
                                departureAt
                        )
        ) {

            throw new BusinessException(
                    ErrorCode
                            .INVALID_TRANSPORT_SEGMENT_TIME
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

    private TransportSegment getTransportSegment(
            Long dayId,
            Long segmentId
    ) {

        return transportSegmentRepository
                .findByIdAndTripDayId(
                        segmentId,
                        dayId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode
                                        .TRANSPORT_SEGMENT_NOT_FOUND
                        )
                );
    }
}