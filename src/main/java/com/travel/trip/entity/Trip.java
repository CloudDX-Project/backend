package com.travel.trip.entity;

import com.travel.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Table(name = "trips")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String departure;

    @Column(nullable = false)
    private Double departureLatitude;

    @Column(nullable = false)
    private Double departureLongitude;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(nullable = false)
    private Double destinationLatitude;

    @Column(nullable = false)
    private Double destinationLongitude;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private int peopleCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MainTransportMode mainTransportMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LocalTransportMode localTransportMode;

    @Column(nullable = false)
    private Long budget;

    @Column(nullable = false)
    private Long mealBudgetPerPersonPerDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripPace pace;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "trip_preferences",
            joinColumns = @JoinColumn(name = "trip_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "preference", nullable = false, length = 30)
    private Set<TripPreference> preferences = new HashSet<>();

    @OneToMany(
            mappedBy = "trip",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("dayNumber ASC")
    private List<TripDay> tripDays = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Trip(
            User user,

            String departure,
            Double departureLatitude,
            Double departureLongitude,

            String destination,
            Double destinationLatitude,
            Double destinationLongitude,

            LocalDate startDate,
            LocalTime startTime,

            LocalDate endDate,
            LocalTime endTime,

            int peopleCount,

            MainTransportMode mainTransportMode,
            LocalTransportMode localTransportMode,

            Long budget,
            Long mealBudgetPerPersonPerDay,

            TripPace pace,

            Set<TripPreference> preferences
    ) {
        this.user = user;

        this.departure = departure;
        this.departureLatitude =
                departureLatitude;
        this.departureLongitude =
                departureLongitude;

        this.destination = destination;
        this.destinationLatitude =
                destinationLatitude;
        this.destinationLongitude =
                destinationLongitude;

        this.startDate = startDate;
        this.startTime = startTime;

        this.endDate = endDate;
        this.endTime = endTime;

        this.peopleCount = peopleCount;

        this.mainTransportMode = mainTransportMode;
        this.localTransportMode = localTransportMode;

        this.budget = budget;
        this.mealBudgetPerPersonPerDay = mealBudgetPerPersonPerDay;

        this.pace = pace;

        this.preferences = new HashSet<>(preferences);
    }

    public void addTripDay(TripDay tripDay) {
        this.tripDays.add(tripDay);
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}