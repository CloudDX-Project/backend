package com.travel.cafe;

import com.travel.cafe.data.CafeData;
import com.travel.cafe.data.CafeMenuData;
import com.travel.cafe.dto.CafeCandidate;
import com.travel.cafe.dto.CafeMenuResponse;
import com.travel.cafe.dto.CafeSearchRequest;
import com.travel.cafe.dto.CafeSearchResponse;
import com.travel.cafe.repository.CafeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class CafeService {

    /*
     * 실제 TMAP/Kakao Routing 전 임시값
     */
    private static final double
            ESTIMATED_DRIVE_SPEED_KMH =
            45.0;

    private final CafeRepository cafeRepository;

    public CafeService(
            CafeRepository cafeRepository
    ) {
        this.cafeRepository =
                cafeRepository;
    }

    @Transactional(readOnly = true)
    public CafeSearchResponse search(
            CafeSearchRequest request
    ) {

        List<NearbyCafe> nearby =
                cafeRepository
                        .findAllLocated()
                        .stream()
                        .map(
                                cafe ->
                                        new NearbyCafe(
                                                cafe,
                                                calculateDistanceKm(
                                                        request.originLatitude(),
                                                        request.originLongitude(),
                                                        cafe.latitude(),
                                                        cafe.longitude()
                                                )
                                        )
                        )
                        .sorted(
                                Comparator.comparingDouble(
                                        NearbyCafe::distanceKm
                                )
                        )
                        .limit(
                                request.resolvedLimit()
                        )
                        .toList();

        List<Long> cafeIds =
                nearby.stream()
                        .map(
                                item ->
                                        item.cafe().id()
                        )
                        .toList();

        Map<Long, List<CafeMenuData>> menuMap =
                cafeRepository.findMenusByCafeIds(
                        cafeIds
                );

        List<CafeCandidate> cafes =
                nearby.stream()
                        .map(
                                item ->
                                        toCandidate(
                                                item,
                                                menuMap.getOrDefault(
                                                        item.cafe().id(),
                                                        List.of()
                                                )
                                        )
                        )
                        .toList();

        return new CafeSearchResponse(
                request.originLatitude(),
                request.originLongitude(),
                cafes.size(),
                "STRAIGHT_LINE_ESTIMATE",
                cafes
        );
    }

    private CafeCandidate toCandidate(
            NearbyCafe nearby,
            List<CafeMenuData> menus
    ) {

        CafeData cafe =
                nearby.cafe();

        return new CafeCandidate(
                cafe.id(),
                cafe.kakaoPlaceId(),
                cafe.cafeName(),
                cafe.category(),
                cafe.phone(),
                cafe.address(),
                cafe.roadAddress(),
                cafe.latitude(),
                cafe.longitude(),
                cafe.placeUrl(),
                cafe.rating(),
                cafe.reviewCount(),
                cafe.businessHours(),
                cafe.summary(),
                cafe.tags(),
                cafe.facilities(),
                cafe.lastScrapedAt(),
                round(
                        nearby.distanceKm(),
                        2
                ),
                calculateEstimatedDriveMinutes(
                        nearby.distanceKm()
                ),
                menus.stream()
                        .map(this::toMenuResponse)
                        .toList()
        );
    }

    private CafeMenuResponse toMenuResponse(
            CafeMenuData menu
    ) {

        return new CafeMenuResponse(
                menu.id(),
                menu.menuName(),
                menu.description(),
                menu.imageUrl(),
                menu.currentPrice(),
                menu.recommended(),
                menu.menuTags()
        );
    }

    private int calculateEstimatedDriveMinutes(
            double distanceKm
    ) {

        return (int) Math.ceil(
                distanceKm
                        /
                        ESTIMATED_DRIVE_SPEED_KMH
                        *
                        60.0
        );
    }

    private double calculateDistanceKm(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        final double earthRadiusKm =
                6371.0;

        double latDistance =
                Math.toRadians(
                        lat2 - lat1
                );

        double lonDistance =
                Math.toRadians(
                        lon2 - lon1
                );

        double a =
                Math.sin(
                        latDistance / 2
                )
                        *
                        Math.sin(
                                latDistance / 2
                        )

                        +

                        Math.cos(
                                Math.toRadians(lat1)
                        )
                                *
                                Math.cos(
                                        Math.toRadians(lat2)
                                )
                                *
                                Math.sin(
                                        lonDistance / 2
                                )
                                *
                                Math.sin(
                                        lonDistance / 2
                                );

        double c =
                2
                        *
                        Math.atan2(
                                Math.sqrt(a),
                                Math.sqrt(
                                        1 - a
                                )
                        );

        return earthRadiusKm * c;
    }

    private double round(
            double value,
            int digits
    ) {

        double scale =
                Math.pow(
                        10,
                        digits
                );

        return Math.round(
                value * scale
        ) / scale;
    }

    private record NearbyCafe(

            CafeData cafe,

            double distanceKm

    ) {
    }
}