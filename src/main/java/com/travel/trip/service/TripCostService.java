package com.travel.trip.service;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.dto.TripCostResponse;
import com.travel.trip.entity.TransportSegment;
import com.travel.trip.entity.Trip;
import com.travel.trip.repository.TransportSegmentRepository;
import com.travel.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripCostService {

    private final TripRepository tripRepository;

    private final TransportSegmentRepository
            transportSegmentRepository;

    public TripCostResponse getTripCost(
            Long userId,
            Long tripId
    ) {

        Trip trip =
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

        long tripDays =
                calculateTripDays(trip);

        long mealCost =
                calculateMealCost(
                        trip,
                        tripDays
                );

        long transportCost =
                calculateTransportCost(
                        tripId
                );

        // 관광지 연동 전
        long activityCost = 0L;

        // 숙박 연동 전
        long accommodationCost = 0L;

        long totalCost =
                mealCost
                        + transportCost
                        + activityCost
                        + accommodationCost;

        long remainingBudget =
                trip.getBudget()
                        - totalCost;

        return new TripCostResponse(
                mealCost,
                transportCost,
                activityCost,
                accommodationCost,
                totalCost,
                remainingBudget
        );
    }

    private long calculateTripDays(
            Trip trip
    ) {

        return ChronoUnit.DAYS.between(
                trip.getStartDate(),
                trip.getEndDate()
        ) + 1;
    }

    private long calculateMealCost(
            Trip trip,
            long tripDays
    ) {

        return trip
                .getMealBudgetPerPersonPerDay()
                * trip.getPeopleCount()
                * tripDays;
    }

    private long calculateTransportCost(
            Long tripId
    ) {

        return transportSegmentRepository
                .findAllByTripIdOrderByDayAndSequence(
                        tripId
                )
                .stream()
                .map(
                        TransportSegment::getCost
                )
                .filter(
                        cost ->
                                cost != null
                                        && cost > 0
                )
                .mapToLong(
                        Long::longValue
                )
                .sum();
    }
}