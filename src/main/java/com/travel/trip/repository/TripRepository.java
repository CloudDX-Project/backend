package com.travel.trip.repository;

import com.travel.trip.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findAllByUserId(Long userId);

    Optional<Trip> findByIdAndUserId(Long tripId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :tripId and t.user.id = :userId")
    Optional<Trip> findOwnedForUpdate(@Param("tripId") Long tripId, @Param("userId") Long userId);
}
