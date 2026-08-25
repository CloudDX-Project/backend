package com.travel.trip.service;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.dto.TripCreateRequest;
import com.travel.trip.dto.TripResponse;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripDay;
import com.travel.trip.repository.TripRepository;
import com.travel.user.entity.User;
import com.travel.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    private void createTripDays(
            Trip trip
    ) {

        long totalDays =
                ChronoUnit.DAYS.between(
                        trip.getStartDate(),
                        trip.getEndDate()
                ) + 1;

        for (int i = 0; i < totalDays; i++) {

            LocalDate date =
                    trip.getStartDate()
                            .plusDays(i);

            TripDay tripDay =
                    TripDay.builder()
                            .trip(trip)
                            .dayNumber(i + 1)
                            .date(date)
                            .build();

            trip.addTripDay(tripDay);
        }
    }

    private void validateTripPeriod(TripCreateRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_TRIP_PERIOD);
        }
    }

    @Transactional
    public TripResponse createTrip(
            Long userId,
            TripCreateRequest request
    ) {
        validateTripPeriod(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        Trip trip = Trip.builder()
                .user(user)
                .departure(request.departure())
                .destination(request.destination())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .peopleCount(request.peopleCount())
                .budget(request.budget())
                .mealBudgetPerPersonPerDay(
                        request.mealBudgetPerPersonPerDay()
                )
                .pace(request.pace())
                .preferences(request.preferences())
                .build();

        createTripDays(trip);

        Trip savedTrip = tripRepository.save(trip);

        return TripResponse.from(savedTrip);
    }

    public TripResponse getTrip(
            Long userId,
            Long tripId
    ) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.TRIP_NOT_FOUND)
                );

        if (!trip.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }

        return TripResponse.from(trip);
    }

    public List<TripResponse> getMyTrips(Long userId) {

        return tripRepository.findAllByUserId(userId)
                .stream()
                .map(TripResponse::from)
                .toList();
    }
}