package com.travel.trip.service;

import com.travel.accommodation.data.AccommodationEnrichmentData;
import com.travel.accommodation.repository.AccommodationEnrichmentRepository;
import com.travel.flight.AirportMapper;
import com.travel.flight.dto.FlightCandidate;
import com.travel.flight.type.FlightDirection;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.dto.TripCreateRequest;
import com.travel.trip.dto.TripResponse;
import com.travel.trip.entity.LocalTransportMode;
import com.travel.trip.entity.MainTransportMode;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripAccommodationSelection;
import com.travel.trip.entity.TripDay;
import com.travel.trip.entity.TripFlight;
import com.travel.trip.entity.TripRentalSelection;
import com.travel.trip.repository.TripRepository;
import com.travel.user.entity.User;
import com.travel.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    private static final String ACCOMMODATION_PROVIDER =
            "NAVER_HOTEL";

    private final TripRepository tripRepository;

    private final UserRepository userRepository;

    private final AccommodationEnrichmentRepository
            accommodationEnrichmentRepository;

    private final AirportMapper airportMapper;

    private void createTripDays(
            Trip trip
    ) {

        long totalDays =
                ChronoUnit.DAYS.between(
                        trip.getStartDate(),
                        trip.getEndDate()
                ) + 1;

        for (
                int i = 0;
                i < totalDays;
                i++
        ) {

            LocalDate date =
                    trip.getStartDate()
                            .plusDays(i);

            TripDay tripDay =
                    TripDay.builder()
                            .trip(trip)
                            .dayNumber(i + 1)
                            .date(date)
                            .build();

            trip.addTripDay(
                    tripDay
            );
        }
    }

    private void validateTripPeriod(
            TripCreateRequest request
    ) {

        if (
                request.endDate()
                        .isBefore(
                                request.startDate()
                        )
        ) {

            throw new BusinessException(
                    ErrorCode.INVALID_TRIP_PERIOD
            );
        }

        if (
                request.startDate()
                        .equals(
                                request.endDate()
                        )
                        &&
                        !request.endTime()
                                .isAfter(
                                        request.startTime()
                                )
        ) {

            throw new BusinessException(
                    ErrorCode.INVALID_TRIP_TIME
            );
        }
    }

    private AccommodationEnrichmentData resolveAccommodation(
            Long accommodationId
    ) {

        AccommodationEnrichmentData accommodation =
                accommodationEnrichmentRepository
                        .findByAccommodationIdAndProvider(
                                accommodationId,
                                ACCOMMODATION_PROVIDER
                        )
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.TRIP_PLAN_ACCOMMODATION_NOT_FOUND
                                        )
                        );

        if (
                accommodation.providerLatitude() == null
                        ||
                        accommodation.providerLongitude() == null
        ) {

            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_ACCOMMODATION_COORDINATES_MISSING
            );
        }

        return accommodation;
    }

    private void validateSelectedFlights(
            TripCreateRequest request
    ) {

        if (
                request.mainTransportMode()
                        != MainTransportMode.AIR
        ) {
            return;
        }

        FlightCandidate outbound =
                request.outboundFlight();

        FlightCandidate returning =
                request.returnFlight();

        if (
                outbound == null
                        ||
                        returning == null
        ) {

            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_FLIGHT_SELECTION_REQUIRED
            );
        }

        if (
                outbound.departureTime() == null
                        ||
                        outbound.arrivalTime() == null
                        ||
                        returning.departureTime() == null
                        ||
                        returning.arrivalTime() == null
        ) {

            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_INVALID_FLIGHT_SELECTION
            );
        }

        String departureAirport =
                airportMapper.resolve(
                        request.departure()
                );

        String destinationAirport =
                airportMapper.resolve(
                        request.destination()
                );

        boolean outboundValid =

                outbound.direction()
                        == FlightDirection.OUTBOUND

                        && equalsAirport(
                        departureAirport,
                        outbound.departureAirport()
                )

                        && equalsAirport(
                        destinationAirport,
                        outbound.arrivalAirport()
                )

                        && outbound.departureTime()
                        .toLocalDate()
                        .equals(
                                request.startDate()
                        )

                        && !outbound.departureTime()
                        .isBefore(
                                LocalDateTime.of(
                                        request.startDate(),
                                        request.startTime()
                                )
                        )

                        && outbound.arrivalTime()
                        .isAfter(
                                outbound.departureTime()
                        );

        boolean returnValid =

                returning.direction()
                        == FlightDirection.RETURN

                        && equalsAirport(
                        destinationAirport,
                        returning.departureAirport()
                )

                        && equalsAirport(
                        departureAirport,
                        returning.arrivalAirport()
                )

                        && returning.departureTime()
                        .toLocalDate()
                        .equals(
                                request.endDate()
                        )

                        && returning.arrivalTime()
                        .isAfter(
                                returning.departureTime()
                        )

                        && !returning.arrivalTime()
                        .isAfter(
                                LocalDateTime.of(
                                        request.endDate(),
                                        request.endTime()
                                )
                        );

        boolean chronological =

                returning.departureTime()
                        .isAfter(
                                outbound.arrivalTime()
                        );

        if (
                !outboundValid
                        ||
                        !returnValid
                        ||
                        !chronological
        ) {

            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_INVALID_FLIGHT_SELECTION
            );
        }
    }

    private void validateSelectedRental(
            TripCreateRequest request
    ) {

        if (
                request.localTransportMode()
                        != LocalTransportMode.RENTAL_CAR
        ) {
            return;
        }

        if (
                request.rental() == null
        ) {

            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_RENTAL_SELECTION_REQUIRED
            );
        }

        if (
                request.rental().latitude() == null
                        ||
                        request.rental().longitude() == null
                        ||
                        request.rental()
                                .estimatedShuttleMinutes() == null
                        ||
                        request.rental()
                                .estimatedShuttleMinutes() <= 0
        ) {

            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_INVALID_RENTAL_SELECTION
            );
        }
    }

    private boolean equalsAirport(
            String expected,
            String actual
    ) {

        return expected != null
                &&
                actual != null
                &&
                expected.equalsIgnoreCase(
                        actual
                );
    }

    private void attachAccommodation(
            Trip trip,
            AccommodationEnrichmentData data
    ) {

        TripAccommodationSelection
                selectedAccommodation =

                TripAccommodationSelection.builder()
                        .trip(trip)

                        .accommodationId(
                                data.accommodationId()
                        )

                        .providerId(
                                data.providerId()
                        )

                        .name(
                                data.providerName()
                        )

                        .address(
                                data.providerAddress()
                        )

                        .latitude(
                                data.providerLatitude()
                        )

                        .longitude(
                                data.providerLongitude()
                        )

                        .checkInTime(
                                data.checkInTime()
                        )

                        .checkOutTime(
                                data.checkOutTime()
                        )

                        .build();

        trip.selectAccommodation(
                selectedAccommodation
        );
    }

    private void attachFlights(
            Trip trip,
            TripCreateRequest request
    ) {

        if (
                request.mainTransportMode()
                        != MainTransportMode.AIR
        ) {
            return;
        }

        trip.addFlight(
                TripFlight.from(
                        trip,
                        request.outboundFlight()
                )
        );

        trip.addFlight(
                TripFlight.from(
                        trip,
                        request.returnFlight()
                )
        );
    }

    private void attachRental(
            Trip trip,
            TripCreateRequest request
    ) {

        if (
                request.localTransportMode()
                        != LocalTransportMode.RENTAL_CAR
        ) {
            return;
        }

        trip.selectRental(
                TripRentalSelection.from(
                        trip,
                        request.rental()
                )
        );
    }

    @Transactional
    public TripResponse createTrip(
            Long userId,
            TripCreateRequest request
    ) {

        validateTripPeriod(
                request
        );

        validateSelectedFlights(
                request
        );

        validateSelectedRental(
                request
        );

        User user =
                userRepository
                        .findById(
                                userId
                        )
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.USER_NOT_FOUND
                                        )
                        );

        AccommodationEnrichmentData accommodation =
                resolveAccommodation(
                        request.accommodationId()
                );

        Trip trip =
                Trip.builder()

                        .user(
                                user
                        )

                        .departure(
                                request.departure()
                        )

                        .departureLatitude(
                                request.departureLatitude()
                        )

                        .departureLongitude(
                                request.departureLongitude()
                        )

                        .destination(
                                request.destination()
                        )

                        .destinationLatitude(
                                request.destinationLatitude()
                        )

                        .destinationLongitude(
                                request.destinationLongitude()
                        )

                        .startDate(
                                request.startDate()
                        )

                        .startTime(
                                request.startTime()
                        )

                        .endDate(
                                request.endDate()
                        )

                        .endTime(
                                request.endTime()
                        )

                        .peopleCount(
                                request.peopleCount()
                        )

                        .mainTransportMode(
                                request.mainTransportMode()
                        )

                        .localTransportMode(
                                request.localTransportMode()
                        )

                        .budget(
                                request.budget()
                        )

                        .mealBudgetPerPersonPerDay(
                                request.mealBudgetPerPersonPerDay()
                        )

                        .pace(
                                request.pace()
                        )

                        .preferences(
                                request.preferences()
                        )

                        .foodPreferences(
                                request.foodPreferences()
                        )

                        .prompt(
                                request.prompt()
                        )

                        .build();

        attachAccommodation(
                trip,
                accommodation
        );

        attachFlights(
                trip,
                request
        );

        attachRental(
                trip,
                request
        );

        createTripDays(
                trip
        );

        Trip savedTrip =
                tripRepository.save(
                        trip
                );

        return TripResponse.from(
                savedTrip
        );
    }

    public TripResponse getTrip(
            Long userId,
            Long tripId
    ) {

        Trip trip =
                tripRepository
                        .findById(
                                tripId
                        )
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.TRIP_NOT_FOUND
                                        )
                        );

        if (
                !trip.getUser()
                        .getId()
                        .equals(
                                userId
                        )
        ) {

            throw new BusinessException(
                    ErrorCode.TRIP_NOT_FOUND
            );
        }

        return TripResponse.from(
                trip
        );
    }

    public List<TripResponse> getMyTrips(
            Long userId
    ) {

        return tripRepository
                .findAllByUserId(
                        userId
                )
                .stream()
                .map(
                        TripResponse::from
                )
                .toList();
    }
}