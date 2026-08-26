package com.travel.trip.repository;

import com.travel.trip.entity.TransportSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransportSegmentRepository
        extends JpaRepository<TransportSegment, Long> {

    List<TransportSegment>
    findAllByTripDayIdOrderBySequenceAsc(
            Long tripDayId
    );

    Optional<TransportSegment>
    findByIdAndTripDayId(
            Long segmentId,
            Long tripDayId
    );

    Optional<TransportSegment>
    findTopByTripDayIdOrderBySequenceDesc(
            Long tripDayId
    );

    @Query("""
            select segment
            from TransportSegment segment
            where segment.tripDay.trip.id = :tripId
            order by
                segment.tripDay.dayNumber asc,
                segment.sequence asc
            """)
    List<TransportSegment>
    findAllByTripIdOrderByDayAndSequence(
            @Param("tripId") Long tripId
    );
}