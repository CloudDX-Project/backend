package com.travel.trip.repository;

import com.travel.trip.entity.TripDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripDayRepository
        extends JpaRepository<TripDay, Long> {

    List<TripDay>
    findAllByTripIdOrderByDayNumberAsc(
            Long tripId
    );

    Optional<TripDay>
    findByIdAndTripId(
            Long dayId,
            Long tripId
    );
}