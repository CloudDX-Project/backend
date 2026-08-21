package com.travel.trip.service;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.dto.TripCostResponse;
import com.travel.trip.entity.Trip;
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

    public TripCostResponse getTripCost(Long userId, Long tripId) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));

        long mealCost = calculateMealCost(trip);

        // 외부 API 연동 전 단계이므로 아직 계산하지 않는 비용은 0원으로 둔다.
        long transportCost = 0L;
        long activityCost = 0L;
        long accommodationCost = 0L;

        long totalCost = mealCost
                + transportCost
                + activityCost
                + accommodationCost;

        long remainingBudget = trip.getBudget() - totalCost;

        return new TripCostResponse(
                mealCost,
                transportCost,
                activityCost,
                accommodationCost,
                totalCost,
                remainingBudget
        );
    }

    private long calculateMealCost(Trip trip) {
        long tripDays = ChronoUnit.DAYS.between(
                trip.getStartDate(),
                trip.getEndDate()
        ) + 1;

        return trip.getMealBudgetPerPersonPerDay()
                * trip.getPeopleCount()
                * tripDays;
    }
}