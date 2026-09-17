package com.travel.trip.repository;

import com.travel.trip.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findAllByUserId(Long userId);

    Optional<Trip> findByIdAndUserId(Long tripId, Long userId);
}