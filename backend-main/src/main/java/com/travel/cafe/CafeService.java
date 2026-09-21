package com.travel.cafe;

import com.travel.global.util.RecommendationMath;

import com.travel.cafe.data.CafeData;
import com.travel.cafe.data.CafeMenuData;
import com.travel.cafe.dto.CafeCandidate;
import com.travel.cafe.dto.CafeDetailResponse;
import com.travel.cafe.dto.CafeMenuResponse;
import com.travel.cafe.dto.CafeSearchRequest;
import com.travel.cafe.dto.CafeSearchResponse;
import com.travel.cafe.repository.CafeRepository;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
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
    public CafeDetailResponse getDetail(
            Long cafeId
    ) {

        CafeData cafe =
                cafeRepository.findById(
                        cafeId
                );

        if (cafe == null) {
            throw new BusinessException(
                    ErrorCode.CAFE_NOT_FOUND
            );
        }

        List<CafeMenuData> menus =
                cafeRepository
                        .findMenusByCafeIds(
                                List.of(cafeId)
                        )
                        .getOrDefault(
                                cafeId,
                                List.of()
                        );

        return CafeDetailResponse.from(
                cafe,
                menus
        );
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
                                                RecommendationMath.distanceKm(
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
                RecommendationMath.round(
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

    private record NearbyCafe(

            CafeData cafe,

            double distanceKm

    ) {
    }
}
