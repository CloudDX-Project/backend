package com.travel.accommodation;

import com.travel.accommodation.data.AccommodationEnrichmentData;
import com.travel.accommodation.dto.AccommodationCandidate;
import com.travel.accommodation.dto.AccommodationSearchRequest;
import com.travel.accommodation.dto.AccommodationSearchResponse;
import com.travel.accommodation.repository.AccommodationEnrichmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccommodationService {

    private static final String NAVER_PROVIDER = "NAVER_HOTEL";

    private final AccommodationEnrichmentRepository enrichmentRepository;

    public AccommodationService(
            AccommodationEnrichmentRepository enrichmentRepository
    ) {
        this.enrichmentRepository = enrichmentRepository;
    }

    /**
     * accommodation_business를 다시 조회하지 않고
     * 이미 매칭/적재가 완료된 accommodation_enrichment만 조회한다.
     */
    @Transactional(readOnly = true)
    public AccommodationSearchResponse search(
            AccommodationSearchRequest request
    ) {

        List<AccommodationEnrichmentData> enrichments =
                enrichmentRepository.findByRegionAndProvider(
                        request.province(),
                        request.city(),
                        request.town(),
                        NAVER_PROVIDER
                );

        List<AccommodationCandidate> candidates =
                enrichments.stream()
                        .map(this::toCandidate)
                        .toList();

        return new AccommodationSearchResponse(
                request.province(),
                request.city(),
                request.town(),

                // 이제 공공데이터 raw -> dedupe 과정을 거치지 않기 때문에
                // 둘 다 실제 조회된 enrichment 개수로 반환
                candidates.size(),
                candidates.size(),

                candidates
        );
    }

    /**
     * 기존 AccommodationCandidate 응답 구조는 유지한다.
     *
     * 공공데이터 전용 필드는 null 처리하고,
     * 숙소명 / 주소 / 좌표는 NAVER_HOTEL 데이터를 사용한다.
     */
    private AccommodationCandidate toCandidate(
            AccommodationEnrichmentData enrichment
    ) {

        return new AccommodationCandidate(

                // id
                enrichment.accommodationId(),

                // businessName
                enrichment.providerName(),

                // businessType
                null,

                // roadAddress
                enrichment.providerAddress(),

                // lotAddress
                null,

                // province
                enrichment.province(),

                // city
                enrichment.city(),

                // town
                enrichment.town(),

                // source
                null,

                // latitude
                enrichment.providerLatitude(),

                // longitude
                enrichment.providerLongitude(),

                // provider
                enrichment.provider(),

                // providerId
                enrichment.providerId(),

                // providerName
                enrichment.providerName(),

                // providerUrl
                enrichment.providerUrl(),

                // providerAddress
                enrichment.providerAddress(),

                // rating
                enrichment.rating(),

                // ratingScale
                enrichment.ratingScale(),

                // reviewCount
                enrichment.reviewCount(),

                // visitorReviewCount
                enrichment.visitorReviewCount(),

                // blogReviewCount
                enrichment.blogReviewCount(),

                // price
                enrichment.price(),

                // priceAvg
                enrichment.priceAvg(),

                // priceText
                enrichment.priceText(),

                // representativeImageUrl
                enrichment.representativeImageUrl(),

                // checkInTime
                enrichment.checkInTime(),

                // checkOutTime
                enrichment.checkOutTime(),

                // starCount
                enrichment.starCount(),

                // description
                enrichment.description(),

                // phoneNumber
                enrichment.phoneNumber()
        );
    }
}