package com.travel.trip.plan.repository;

import com.travel.trip.plan.entity.TripPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripPlanItemRepository
        extends JpaRepository<TripPlanItem, Long> {

    List<TripPlanItem>
    findAllByTripDayIdOrderByItemOrderAsc(
            Long tripDayId
    );
}