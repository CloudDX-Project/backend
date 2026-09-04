package com.travel.accommodation;

import com.travel.accommodation.dto.AccommodationCandidate;
import com.travel.accommodation.dto.AccommodationSearchRequest;
import com.travel.accommodation.dto.AccommodationSearchResponse;
import com.travel.accommodation.entity.AccommodationBusiness;
import com.travel.accommodation.data.AccommodationEnrichmentData;
import com.travel.accommodation.repository.AccommodationBusinessRepository;
import com.travel.accommodation.repository.AccommodationEnrichmentRepository;
import com.travel.accommodation.type.AccommodationBusinessStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccommodationService {

    private static final String NAVER_PROVIDER = "NAVER_HOTEL";

    private final AccommodationBusinessRepository businessRepository;
    private final AccommodationEnrichmentRepository enrichmentRepository;
    private final AccommodationDeduplicator deduplicator;

    public AccommodationService(
            AccommodationBusinessRepository businessRepository,
            AccommodationEnrichmentRepository enrichmentRepository,
            AccommodationDeduplicator deduplicator
    ) {
        this.businessRepository = businessRepository;
        this.enrichmentRepository = enrichmentRepository;
        this.deduplicator = deduplicator;
    }

    /**
     * 외부 Places API 호출 없이 DB 데이터만으로 숙소를 검색한다.
     */
    @Transactional(readOnly = true)
    public AccommodationSearchResponse search(
            AccommodationSearchRequest request
    ) {

        List<AccommodationBusiness> rawBusinesses =
                findBusinesses(request);

        Map<Long, AccommodationEnrichmentData> enrichmentByAccommodationId =
                findNaverEnrichments(rawBusinesses);

        /*
         * 중복 공공데이터 중 한쪽에만 enrichment가 연결되어 있을 수 있으므로
         * enrichment가 있는 row를 대표 row로 우선 선택한다.
         */
        List<AccommodationBusiness> deduplicated =
                deduplicator.deduplicate(
                        rawBusinesses,
                        enrichmentByAccommodationId.keySet()
                );

        List<AccommodationCandidate> candidates =
                deduplicated
                        .stream()
                        .map(business -> toCandidate(
                                business,
                                enrichmentByAccommodationId.get(business.getId())
                        ))
                        .toList();

        return new AccommodationSearchResponse(
                request.province(),
                request.city(),
                request.town(),
                rawBusinesses.size(),
                candidates.size(),
                candidates
        );
    }

    private List<AccommodationBusiness> findBusinesses(
            AccommodationSearchRequest request
    ) {

        if (request.town() != null
                && !request.town().isBlank()) {

            return businessRepository
                    .findByProvinceAndCityAndTownAndBusinessStatus(
                            request.province(),
                            request.city(),
                            request.town(),
                            AccommodationBusinessStatus.OPEN
                    );
        }

        return businessRepository
                .findByProvinceAndCityAndBusinessStatus(
                        request.province(),
                        request.city(),
                        AccommodationBusinessStatus.OPEN
                );
    }

    private Map<Long, AccommodationEnrichmentData> findNaverEnrichments(
            List<AccommodationBusiness> businesses
    ) {

        if (businesses.isEmpty()) {
            return Map.of();
        }

        List<Long> accommodationIds = businesses
                .stream()
                .map(AccommodationBusiness::getId)
                .toList();

        return enrichmentRepository
                .findByAccommodationIdInAndProvider(
                        accommodationIds,
                        NAVER_PROVIDER
                )
                .stream()
                /*
                 * DB unique(accommodation_id, provider)가 정상이라면 중복은 없지만,
                 * 혹시 기존 데이터에 중복이 있어도 최신 updated_at을 사용한다.
                 */
                .collect(Collectors.toMap(
                        AccommodationEnrichmentData::accommodationId,
                        Function.identity(),
                        this::selectLatestEnrichment
                ));
    }

    private AccommodationEnrichmentData selectLatestEnrichment(
            AccommodationEnrichmentData first,
            AccommodationEnrichmentData second
    ) {
        if (first.updatedAt() == null) {
            return second;
        }
        if (second.updatedAt() == null) {
            return first;
        }
        return second.updatedAt().isAfter(first.updatedAt())
                ? second
                : first;
    }

    private AccommodationCandidate toCandidate(
            AccommodationBusiness business,
            AccommodationEnrichmentData enrichment
    ) {

        Double latitude = firstNonNull(
                enrichment == null ? null : enrichment.providerLatitude(),
                business.getLatitude()
        );

        Double longitude = firstNonNull(
                enrichment == null ? null : enrichment.providerLongitude(),
                business.getLongitude()
        );

        return new AccommodationCandidate(
                business.getId(),
                business.getBusinessName(),
                business.getBusinessType(),
                business.getRoadAddress(),
                business.getLotAddress(),
                business.getProvince(),
                business.getCity(),
                business.getTown(),
                business.getSource(),
                latitude,
                longitude,
                enrichment == null ? null : enrichment.provider(),
                enrichment == null ? null : enrichment.providerId(),
                enrichment == null ? null : enrichment.providerName(),
                enrichment == null ? null : enrichment.providerUrl(),
                enrichment == null ? null : enrichment.providerAddress(),
                enrichment == null ? null : enrichment.rating(),
                enrichment == null ? null : enrichment.ratingScale(),
                enrichment == null ? null : enrichment.reviewCount(),
                enrichment == null ? null : enrichment.visitorReviewCount(),
                enrichment == null ? null : enrichment.blogReviewCount(),
                enrichment == null ? null : enrichment.price(),
                enrichment == null ? null : enrichment.priceAvg(),
                enrichment == null ? null : enrichment.priceText(),
                enrichment == null ? null : enrichment.representativeImageUrl(),
                enrichment == null ? null : enrichment.checkInTime(),
                enrichment == null ? null : enrichment.checkOutTime(),
                enrichment == null ? null : enrichment.starCount(),
                enrichment == null ? null : enrichment.description(),
                enrichment == null ? null : enrichment.phoneNumber()
        );
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }
}
