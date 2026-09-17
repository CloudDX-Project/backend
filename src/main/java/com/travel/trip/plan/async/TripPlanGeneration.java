package com.travel.trip.plan.async;

import com.travel.trip.entity.Trip;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "trip_plan_generations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trip_plan_generation_trip",
                columnNames = "trip_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPlanGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripPlanGenerationStatus status;

    @Lob
    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    private TripPlanGeneration(
            Trip trip,
            String requestId,
            LocalDateTime now
    ) {
        this.trip = trip;
        restart(requestId, now);
    }

    public static TripPlanGeneration pending(
            Trip trip,
            String requestId,
            LocalDateTime now
    ) {
        return new TripPlanGeneration(trip, requestId, now);
    }

    public void restart(
            String nextRequestId,
            LocalDateTime now
    ) {
        requestId = nextRequestId;
        status = TripPlanGenerationStatus.PENDING;
        resultJson = null;
        errorMessage = null;
        requestedAt = now;
        startedAt = null;
        completedAt = null;
        updatedAt = now;
    }

    public boolean isActive() {
        return status == TripPlanGenerationStatus.PENDING
                || status == TripPlanGenerationStatus.PROCESSING;
    }

    public boolean matches(String candidateRequestId) {
        return requestId.equals(candidateRequestId);
    }

    public void markProcessing(LocalDateTime now) {
        status = TripPlanGenerationStatus.PROCESSING;
        startedAt = startedAt == null ? now : startedAt;
        updatedAt = now;
        errorMessage = null;
    }

    public void markCompleted(
            String json,
            LocalDateTime now
    ) {
        status = TripPlanGenerationStatus.COMPLETED;
        resultJson = json;
        errorMessage = null;
        completedAt = now;
        updatedAt = now;
    }

    public void markFailed(
            String message,
            LocalDateTime now
    ) {
        status = TripPlanGenerationStatus.FAILED;
        resultJson = null;
        errorMessage = truncate(message);
        completedAt = now;
        updatedAt = now;
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "여행 일정 생성 중 오류가 발생했습니다.";
        }
        return value.length() <= 1000
                ? value
                : value.substring(0, 1000);
    }
}
