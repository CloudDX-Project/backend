package com.travel.trip.plan.async;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripPlanGenerationRepository
        extends JpaRepository<TripPlanGeneration, Long> {

    Optional<TripPlanGeneration> findByTrip_Id(Long tripId);
}
