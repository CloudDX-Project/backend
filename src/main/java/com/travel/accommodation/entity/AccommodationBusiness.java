package com.travel.accommodation.entity;

import com.travel.accommodation.type.AccommodationBusinessStatus;
import com.travel.accommodation.type.AccommodationSource;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "accommodation_business",
        indexes = {
                @Index(
                        name = "idx_accommodation_region_status",
                        columnList = "province, city, town, business_status"
                ),
                @Index(
                        name = "idx_accommodation_business_name",
                        columnList = "business_name"
                ),
                @Index(
                        name = "idx_accommodation_road_address",
                        columnList = "road_address"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_accommodation_source_localgov_management",
                        columnNames = {
                                "source",
                                "local_government_code",
                                "management_number"
                        }
                )
        }
)
public class AccommodationBusiness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 데이터 출처
     *
     * LODGING
     * TOURIST_LODGING
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccommodationSource source;

    /**
     * 개방자치단체코드
     *
     * OPN_ATMY_GRP_CD
     *
     * 예:
     * 제주시 6510000
     */
    @Column(
            name = "local_government_code",
            nullable = false,
            length = 20
    )
    private String localGovernmentCode;

    /**
     * 행안부 관리번호
     *
     * MNG_NO
     */
    @Column(
            name = "management_number",
            nullable = false,
            length = 100
    )
    private String managementNumber;

    /**
     * 사업장명
     *
     * BPLC_NM
     */
    @Column(
            name = "business_name",
            nullable = false,
            length = 255
    )
    private String businessName;

    /**
     * 숙박업 세부유형
     *
     * 일반 숙박업:
     * SNTTN_BZSTAT_NM
     *
     * 관광숙박업:
     * TOUR_LDGBIZ_DTL_NM
     */
    @Column(
            name = "business_type",
            length = 100
    )
    private String businessType;

    /**
     * 행안부 원본 영업상태 코드
     *
     * SALS_STTS_CD
     */
    @Column(
            name = "business_status_code",
            length = 30
    )
    private String businessStatusCode;

    /**
     * 우리 시스템의 정규화 영업상태
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "business_status",
            nullable = false,
            length = 30
    )
    private AccommodationBusinessStatus businessStatus;

    /**
     * 도로명 주소
     *
     * ROAD_NM_ADDR
     */
    @Column(
            name = "road_address",
            length = 500
    )
    private String roadAddress;

    /**
     * 지번 주소
     *
     * LOTNO_ADDR
     */
    @Column(
            name = "lot_address",
            length = 500
    )
    private String lotAddress;

    /**
     * 주소 정규화
     *
     * 제주특별자치도 / 제주시 / 애월읍
     */
    @Column(length = 100)
    private String province;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String town;

    /**
     * 인허가일자
     *
     * LCPMT_YMD
     */
    @Column(name = "license_date")
    private LocalDate licenseDate;

    /**
     * 폐업일자
     *
     * CLSBIZ_YMD
     */
    @Column(name = "closed_date")
    private LocalDate closedDate;

    /**
     * 행안부 데이터 갱신시점
     *
     * DAT_UPDT_PNT
     */
    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;


    /*
     * 좌표는 공공데이터 또는 별도 수집 데이터에서 적재될 수 있다.
     * 외부 Places 전용 필드는 더 이상 이 엔티티에서 관리하지 않는다.
     */

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;


    protected AccommodationBusiness() {
    }


    public AccommodationBusiness(
            AccommodationSource source,
            String localGovernmentCode,
            String managementNumber,
            String businessName,
            String businessType,
            String businessStatusCode,
            AccommodationBusinessStatus businessStatus,
            String roadAddress,
            String lotAddress,
            String province,
            String city,
            String town,
            LocalDate licenseDate,
            LocalDate closedDate,
            LocalDateTime sourceUpdatedAt
    ) {
        this.source = source;
        this.localGovernmentCode = localGovernmentCode;
        this.managementNumber = managementNumber;
        this.businessName = businessName;
        this.businessType = businessType;
        this.businessStatusCode = businessStatusCode;
        this.businessStatus = businessStatus;
        this.roadAddress = roadAddress;
        this.lotAddress = lotAddress;
        this.province = province;
        this.city = city;
        this.town = town;
        this.licenseDate = licenseDate;
        this.closedDate = closedDate;
        this.sourceUpdatedAt = sourceUpdatedAt;
    }


    public void updateLicenseData(
            String businessName,
            String businessType,
            String businessStatusCode,
            AccommodationBusinessStatus businessStatus,
            String roadAddress,
            String lotAddress,
            String province,
            String city,
            String town,
            LocalDate licenseDate,
            LocalDate closedDate,
            LocalDateTime sourceUpdatedAt
    ) {
        this.businessName = businessName;
        this.businessType = businessType;
        this.businessStatusCode = businessStatusCode;
        this.businessStatus = businessStatus;
        this.roadAddress = roadAddress;
        this.lotAddress = lotAddress;
        this.province = province;
        this.city = city;
        this.town = town;
        this.licenseDate = licenseDate;
        this.closedDate = closedDate;
        this.sourceUpdatedAt = sourceUpdatedAt;
    }


    public Long getId() {
        return id;
    }

    public AccommodationSource getSource() {
        return source;
    }

    public String getLocalGovernmentCode() {
        return localGovernmentCode;
    }

    public String getManagementNumber() {
        return managementNumber;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getBusinessType() {
        return businessType;
    }

    public String getBusinessStatusCode() {
        return businessStatusCode;
    }

    public AccommodationBusinessStatus getBusinessStatus() {
        return businessStatus;
    }

    public String getRoadAddress() {
        return roadAddress;
    }

    public String getLotAddress() {
        return lotAddress;
    }

    public String getProvince() {
        return province;
    }

    public String getCity() {
        return city;
    }

    public String getTown() {
        return town;
    }

    public LocalDate getLicenseDate() {
        return licenseDate;
    }

    public LocalDate getClosedDate() {
        return closedDate;
    }

    public LocalDateTime getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }




    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }


}