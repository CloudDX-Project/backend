package com.travel.accommodation.repository;

import com.travel.accommodation.entity.AccommodationBusiness;
import com.travel.accommodation.type.AccommodationBusinessStatus;
import com.travel.accommodation.type.AccommodationSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccommodationBusinessRepository
        extends JpaRepository<AccommodationBusiness, Long> {

    Optional<AccommodationBusiness>
    findBySourceAndLocalGovernmentCodeAndManagementNumber(
            AccommodationSource source,
            String localGovernmentCode,
            String managementNumber
    );

    List<AccommodationBusiness>
    findBySourceAndManagementNumberIn(
            AccommodationSource source,
            Collection<String> managementNumbers
    );

    /*
     * 읍/면/동까지 지정했을 때
     */
    List<AccommodationBusiness>
    findByProvinceAndCityAndTownAndBusinessStatus(
            String province,
            String city,
            String town,
            AccommodationBusinessStatus businessStatus
    );

    /*
     * 시/군/구까지만 지정했을 때
     */
    List<AccommodationBusiness>
    findByProvinceAndCityAndBusinessStatus(
            String province,
            String city,
            AccommodationBusinessStatus businessStatus
    );
}