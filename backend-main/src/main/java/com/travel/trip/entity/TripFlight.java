package com.travel.trip.entity;

import com.travel.flight.dto.FlightCandidate;
import com.travel.flight.type.FlightDirection;
import com.travel.flight.type.FlightPriceType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "trip_flights",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_trip_flight_trip_direction",
                        columnNames = {
                                "trip_id",
                                "direction"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripFlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "external_flight_id", length = 150)
    private String externalFlightId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlightDirection direction;

    @Column(length = 100)
    private String airline;

    @Column(name = "airline_code", length = 20)
    private String airlineCode;

    @Column(name = "flight_number", length = 30)
    private String flightNumber;

    @Column(name = "departure_airport", nullable = false, length = 10)
    private String departureAirport;

    @Column(name = "arrival_airport", nullable = false, length = 10)
    private String arrivalAirport;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Column(name = "estimated_price_per_person", nullable = false)
    private int estimatedPricePerPerson;

    @Column(name = "estimated_total_price", nullable = false)
    private int estimatedTotalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 30)
    private FlightPriceType priceType;

    @Column(length = 100)
    private String aircraft;

    @Column(length = 100)
    private String status;

    @Builder
    public TripFlight(
            Trip trip,
            String externalFlightId,
            FlightDirection direction,
            String airline,
            String airlineCode,
            String flightNumber,
            String departureAirport,
            String arrivalAirport,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            int estimatedPricePerPerson,
            int estimatedTotalPrice,
            FlightPriceType priceType,
            String aircraft,
            String status
    ) {
        this.trip = trip;
        this.externalFlightId = externalFlightId;
        this.direction = direction;
        this.airline = airline;
        this.airlineCode = airlineCode;
        this.flightNumber = flightNumber;
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.estimatedPricePerPerson = estimatedPricePerPerson;
        this.estimatedTotalPrice = estimatedTotalPrice;
        this.priceType = priceType == null
                ? FlightPriceType.ESTIMATED
                : priceType;
        this.aircraft = aircraft;
        this.status = status;
    }

    public static TripFlight from(
            Trip trip,
            FlightCandidate candidate
    ) {
        return TripFlight.builder()
                .trip(trip)
                .externalFlightId(candidate.id())
                .direction(candidate.direction())
                .airline(candidate.airline())
                .airlineCode(candidate.airlineCode())
                .flightNumber(candidate.flightNumber())
                .departureAirport(candidate.departureAirport())
                .arrivalAirport(candidate.arrivalAirport())
                .departureTime(candidate.departureTime())
                .arrivalTime(candidate.arrivalTime())
                .estimatedPricePerPerson(candidate.estimatedPricePerPerson())
                .estimatedTotalPrice(candidate.estimatedTotalPrice())
                .priceType(candidate.priceType())
                .aircraft(candidate.aircraft())
                .status(candidate.status())
                .build();
    }

    public FlightCandidate toCandidate() {
        return new FlightCandidate(
                externalFlightId,
                direction,
                airline,
                airlineCode,
                flightNumber,
                departureAirport,
                arrivalAirport,
                departureTime,
                arrivalTime,
                estimatedPricePerPerson,
                estimatedTotalPrice,
                priceType,
                aircraft,
                status
        );
    }
}