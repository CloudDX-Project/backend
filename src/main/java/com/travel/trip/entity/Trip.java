package com.travel.trip.entity;

import com.travel.flight.dto.FlightCandidate;
import com.travel.flight.type.FlightDirection;
import com.travel.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Column(
            nullable = false,
            length = 100
    )
    private String departure;

    @Column(nullable = false)
    private Double departureLatitude;

    @Column(nullable = false)
    private Double departureLongitude;

    @Column(
            nullable = false,
            length = 100
    )
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
    @Column(
            nullable = false,
            length = 30
    )
    private MainTransportMode mainTransportMode;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private LocalTransportMode localTransportMode;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "fuel_type",
            length = 20
    )
    private VehicleFuelType fuelType;

    @Column(name = "vehicle_efficiency_kmpl")
    private Double vehicleEfficiencyKmpl;

    @Column(nullable = false)
    private Long budget;

    @Column(nullable = false)
    private Long mealBudgetPerPersonPerDay;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private TripPace pace;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "trip_preferences",
            joinColumns = @JoinColumn(name = "trip_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(
            name = "preference",
            nullable = false,
            length = 30
    )
    private Set<TripPreference> preferences =
            new HashSet<>();

    @Column(length = 1000)
    private String prompt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "trip_food_preferences",
            joinColumns = @JoinColumn(name = "trip_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(
            name = "food_preference",
            nullable = false,
            length = 30
    )
    private Set<FoodPreference> foodPreferences =
            new HashSet<>();

    /*
     * 선택 숙소.
     */
    @OneToOne(
            mappedBy = "trip",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private TripAccommodationSelection selectedAccommodation;

    /*
     * 선택 항공편.
     */
    @OneToMany(
            mappedBy = "trip",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<TripFlight> flights =
            new ArrayList<>();

    /*
     * 선택 렌터카.
     */
    @OneToOne(
            mappedBy = "trip",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private TripRentalSelection selectedRental;

    @OneToMany(
            mappedBy = "trip",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("dayNumber ASC")
    private List<TripDay> tripDays =
            new ArrayList<>();

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
            VehicleFuelType fuelType,
            Double vehicleEfficiencyKmpl,
            Long budget,
            Long mealBudgetPerPersonPerDay,
            TripPace pace,
            Set<TripPreference> preferences,
            Set<FoodPreference> foodPreferences,
            String prompt
    ) {
        this.user = user;

        this.departure = departure;
        this.departureLatitude = departureLatitude;
        this.departureLongitude = departureLongitude;

        this.destination = destination;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;

        this.startDate = startDate;
        this.startTime = startTime;

        this.endDate = endDate;
        this.endTime = endTime;

        this.peopleCount = peopleCount;

        this.mainTransportMode =
                mainTransportMode;

        this.localTransportMode =
                localTransportMode;

        this.fuelType = fuelType;
        this.vehicleEfficiencyKmpl = vehicleEfficiencyKmpl;

        this.budget = budget;

        this.mealBudgetPerPersonPerDay =
                mealBudgetPerPersonPerDay;

        this.pace = pace;

        this.preferences =
                preferences == null
                        ? new HashSet<>()
                        : new HashSet<>(preferences);

        this.foodPreferences =
                foodPreferences == null
                        ? new HashSet<>()
                        : new HashSet<>(foodPreferences);

        this.prompt =
                prompt == null || prompt.isBlank()
                        ? null
                        : prompt.trim();
    }

    public void selectAccommodation(
            TripAccommodationSelection accommodation
    ) {
        this.selectedAccommodation =
                accommodation;
    }

    public void addFlight(
            TripFlight flight
    ) {
        this.flights.add(
                flight
        );
    }

    public void selectRental(
            TripRentalSelection rental
    ) {
        this.selectedRental =
                rental;
    }

    public FlightCandidate getOutboundFlightCandidate() {
        return flights.stream()
                .filter(
                        flight ->
                                flight.getDirection()
                                        == FlightDirection.OUTBOUND
                )
                .findFirst()
                .map(
                        TripFlight::toCandidate
                )
                .orElse(null);
    }

    public FlightCandidate getReturnFlightCandidate() {
        return flights.stream()
                .filter(
                        flight ->
                                flight.getDirection()
                                        == FlightDirection.RETURN
                )
                .findFirst()
                .map(
                        TripFlight::toCandidate
                )
                .orElse(null);
    }

    public void addTripDay(
            TripDay tripDay
    ) {
        this.tripDays.add(
                tripDay
        );
    }

    @PrePersist
    public void prePersist() {

        LocalDateTime now =
                LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {

        this.updatedAt =
                LocalDateTime.now();
    }
}