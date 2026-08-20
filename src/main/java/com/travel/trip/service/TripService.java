package com.travel.trip.service;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.dto.TripCreateRequest;
import com.travel.trip.dto.TripResponse;
import com.travel.trip.entity.Trip;
import com.travel.trip.repository.TripRepository;
import com.travel.user.entity.User;
import com.travel.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    @Transactional
    public TripResponse createTrip(
            Long userId,
            TripCreateRequest request
    ) {
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
                .build();

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