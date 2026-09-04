package com.travel.accommodation;

import com.travel.accommodation.entity.AccommodationBusiness;
import com.travel.accommodation.type.AccommodationSource;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AccommodationDeduplicator {

    public List<AccommodationBusiness> deduplicate(
            List<AccommodationBusiness> businesses
    ) {
        return deduplicate(businesses, Set.of());
    }

    /**
     * 동일 숙소로 판단되는 공공데이터 row 중 대표 row를 선택한다.
     *
     * 우선순위:
     * 1) accommodation_enrichment가 연결된 row
     * 2) 좌표가 있는 row
     * 3) 도로명 주소가 있는 row
     * 4) TOURIST_LODGING
     * 5) 작은 id
     */
    public List<AccommodationBusiness> deduplicate(
            List<AccommodationBusiness> businesses,
            Set<Long> enrichedAccommodationIds
    ) {

        Map<String, AccommodationBusiness> uniqueMap =
                new LinkedHashMap<>();

        for (AccommodationBusiness business : businesses) {

            String key = buildKey(business);
            AccommodationBusiness existing = uniqueMap.get(key);

            if (existing == null) {
                uniqueMap.put(key, business);
                continue;
            }

            uniqueMap.put(
                    key,
                    selectPreferred(
                            existing,
                            business,
                            enrichedAccommodationIds
                    )
            );
        }

        return new ArrayList<>(uniqueMap.values());
    }

    private AccommodationBusiness selectPreferred(
            AccommodationBusiness first,
            AccommodationBusiness second,
            Set<Long> enrichedAccommodationIds
    ) {

        boolean firstEnriched = enrichedAccommodationIds.contains(first.getId());
        boolean secondEnriched = enrichedAccommodationIds.contains(second.getId());

        if (firstEnriched != secondEnriched) {
            return firstEnriched ? first : second;
        }

        boolean firstHasCoordinates = hasCoordinates(first);
        boolean secondHasCoordinates = hasCoordinates(second);

        if (firstHasCoordinates != secondHasCoordinates) {
            return firstHasCoordinates ? first : second;
        }

        boolean firstHasRoadAddress = hasText(first.getRoadAddress());
        boolean secondHasRoadAddress = hasText(second.getRoadAddress());

        if (firstHasRoadAddress != secondHasRoadAddress) {
            return firstHasRoadAddress ? first : second;
        }

        if (first.getSource() != second.getSource()) {
            if (first.getSource() == AccommodationSource.TOURIST_LODGING) {
                return first;
            }
            if (second.getSource() == AccommodationSource.TOURIST_LODGING) {
                return second;
            }
        }

        if (first.getId() == null) {
            return second;
        }
        if (second.getId() == null) {
            return first;
        }

        return first.getId() <= second.getId() ? first : second;
    }

    private String buildKey(
            AccommodationBusiness business
    ) {

        String name = normalizeName(business.getBusinessName());
        String address = normalizeAddress(getAddress(business));

        return name + "|" + address;
    }

    private String getAddress(
            AccommodationBusiness business
    ) {
        if (hasText(business.getRoadAddress())) {
            return business.getRoadAddress();
        }
        return business.getLotAddress();
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }

        return value
                .toLowerCase(Locale.ROOT)
                .replace("주식회사", "")
                .replace("(주)", "")
                .replace("㈜", "")
                .replace("&", "앤")
                .replaceAll("[\\s\\p{Punct}]", "");
    }

    private String normalizeAddress(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .replace("제주특별자치도", "제주")
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("[,\\s]+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private boolean hasCoordinates(AccommodationBusiness business) {
        return business.getLatitude() != null
                && business.getLongitude() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
