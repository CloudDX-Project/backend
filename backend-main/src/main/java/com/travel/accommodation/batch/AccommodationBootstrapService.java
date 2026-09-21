package com.travel.accommodation.batch;

import com.travel.accommodation.entity.AccommodationBusiness;
import com.travel.accommodation.repository.AccommodationBusinessRepository;
import com.travel.accommodation.type.AccommodationBusinessStatus;
import com.travel.accommodation.type.AccommodationSource;
import com.travel.external.accommodation.LodgingLicenseClient;
import com.travel.external.accommodation.TouristLodgingLicenseClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccommodationBootstrapService {

    private final LodgingLicenseClient lodgingClient;

    private final TouristLodgingLicenseClient
            touristLodgingClient;

    private final AccommodationBusinessRepository
            repository;

    private final AccommodationRegionParser
            regionParser;


    public AccommodationBootstrapService(
            LodgingLicenseClient lodgingClient,
            TouristLodgingLicenseClient touristLodgingClient,
            AccommodationBusinessRepository repository,
            AccommodationRegionParser regionParser
    ) {
        this.lodgingClient =
                lodgingClient;

        this.touristLodgingClient =
                touristLodgingClient;

        this.repository =
                repository;

        this.regionParser =
                regionParser;
    }


    /*
     * ==========================================
     * 전체 Bootstrap
     * ==========================================
     */

    public BootstrapResult bootstrap() {

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Accommodation Bootstrap START"
        );

        System.out.println(
                "========================================"
        );


        ImportResult lodgingResult =
                importLodging();


        ImportResult touristResult =
                importTouristLodging();


        BootstrapResult result =
                new BootstrapResult(
                        lodgingResult,
                        touristResult,
                        lodgingResult.processedCount()
                                + touristResult.processedCount()
                );


        System.out.println(
                "========================================"
        );

        System.out.println(
                "Accommodation Bootstrap COMPLETE"
        );

        System.out.println(
                "LODGING = "
                        + lodgingResult.processedCount()
        );

        System.out.println(
                "TOURIST_LODGING = "
                        + touristResult.processedCount()
        );

        System.out.println(
                "TOTAL = "
                        + result.totalProcessedCount()
        );

        System.out.println(
                "========================================"
        );


        return result;
    }


    /*
     * ==========================================
     * 일반 숙박업
     * ==========================================
     */

    private ImportResult importLodging() {

        int page = 1;

        int processedCount = 0;
        int insertedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;


        while (true) {

            LodgingLicenseClient.Page result =
                    lodgingClient.getPage(page);


            if (result.items().isEmpty()) {
                break;
            }


            PageSaveResult saveResult =
                    saveLodgingPage(
                            result.items()
                    );


            processedCount +=
                    result.items().size();

            insertedCount +=
                    saveResult.insertedCount();

            updatedCount +=
                    saveResult.updatedCount();

            skippedCount +=
                    saveResult.skippedCount();


            int totalPages =
                    calculateTotalPages(
                            result.totalCount(),
                            result.numOfRows()
                    );


            System.out.printf(
                    "[LODGING] page %d / %d, processed=%d/%d, insert=%d, update=%d, skip=%d%n",
                    page,
                    totalPages,
                    processedCount,
                    result.totalCount(),
                    insertedCount,
                    updatedCount,
                    skippedCount
            );


            if (page >= totalPages) {
                break;
            }


            page++;
        }


        return new ImportResult(
                processedCount,
                insertedCount,
                updatedCount,
                skippedCount
        );
    }


    private PageSaveResult saveLodgingPage(
            List<LodgingLicenseClient.Item> items
    ) {

        List<String> managementNumbers =
                items.stream()
                        .map(
                                LodgingLicenseClient.Item
                                        ::managementNumber
                        )
                        .filter(Objects::nonNull)
                        .filter(
                                value ->
                                        !value.isBlank()
                        )
                        .distinct()
                        .toList();


        Map<BusinessKey, AccommodationBusiness>
                existingMap =

                repository
                        .findBySourceAndManagementNumberIn(
                                AccommodationSource.LODGING,
                                managementNumbers
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(

                                        business ->
                                                new BusinessKey(
                                                        business
                                                                .getLocalGovernmentCode(),
                                                        business
                                                                .getManagementNumber()
                                                ),

                                        Function.identity(),

                                        (first, second) ->
                                                first
                                )
                        );


        List<AccommodationBusiness> saveList =
                new ArrayList<>();


        Set<BusinessKey> pageKeys =
                new HashSet<>();


        int inserted = 0;
        int updated = 0;
        int skipped = 0;


        for (
                LodgingLicenseClient.Item item
                : items
        ) {

            if (isBlank(item.managementNumber())
                    || isBlank(
                    item.localGovernmentCode()
            )
                    || isBlank(
                    item.businessName()
            )) {

                skipped++;
                continue;
            }


            BusinessKey key =
                    new BusinessKey(
                            item.localGovernmentCode(),
                            item.managementNumber()
                    );


            /*
             * 같은 API 페이지 안에
             * 동일 composite key가 중복되어도
             * 한 번만 처리
             */
            if (!pageKeys.add(key)) {

                skipped++;
                continue;
            }


            AccommodationRegionParser.Region region =
                    regionParser.parse(
                            item.roadAddress(),
                            item.lotAddress()
                    );


            AccommodationBusinessStatus status =
                    resolveStatus(
                            item.salesStatusCode(),
                            item.salesStatusName()
                    );


            AccommodationBusiness existing =
                    existingMap.get(key);


            LocalDateTime incomingUpdatedAt =
                    parseDateTime(
                            item.dataUpdatedAt()
                    );


            if (existing == null) {

                AccommodationBusiness business =
                        new AccommodationBusiness(

                                AccommodationSource.LODGING,

                                item.localGovernmentCode(),

                                item.managementNumber(),

                                item.businessName(),

                                item.businessType(),

                                item.salesStatusCode(),

                                status,

                                emptyToNull(
                                        item.roadAddress()
                                ),

                                emptyToNull(
                                        item.lotAddress()
                                ),

                                region.province(),

                                region.city(),

                                region.town(),

                                parseDate(
                                        item.licenseDate()
                                ),

                                parseDate(
                                        item.closedDate()
                                ),

                                incomingUpdatedAt
                        );


                saveList.add(business);

                /*
                 * 같은 bootstrap 중 이후 처리에서도
                 * 사용할 수 있도록 Map에 추가
                 */
                existingMap.put(
                        key,
                        business
                );

                inserted++;

            } else {

                /*
                 * 행안부 데이터 갱신시점이 같으면
                 * 실제 내용도 동일하다고 보고 SKIP
                 */
                if (Objects.equals(
                        existing.getSourceUpdatedAt(),
                        incomingUpdatedAt
                )) {

                    skipped++;
                    continue;
                }


                existing.updateLicenseData(

                        item.businessName(),

                        item.businessType(),

                        item.salesStatusCode(),

                        status,

                        emptyToNull(
                                item.roadAddress()
                        ),

                        emptyToNull(
                                item.lotAddress()
                        ),

                        region.province(),

                        region.city(),

                        region.town(),

                        parseDate(
                                item.licenseDate()
                        ),

                        parseDate(
                                item.closedDate()
                        ),

                        incomingUpdatedAt
                );


                saveList.add(existing);

                updated++;
            }
        }


        if (!saveList.isEmpty()) {
            repository.saveAll(saveList);
        }


        return new PageSaveResult(
                inserted,
                updated,
                skipped
        );
    }


    /*
     * ==========================================
     * 관광숙박업
     * ==========================================
     */

    private ImportResult importTouristLodging() {

        int page = 1;

        int processedCount = 0;
        int insertedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;


        while (true) {

            TouristLodgingLicenseClient.Page result =
                    touristLodgingClient.getPage(page);


            if (result.items().isEmpty()) {
                break;
            }


            if (page == 1) {

                System.out.println(
                        "[TOURIST_LODGING] API totalCount = "
                                + result.totalCount()
                );
            }


            PageSaveResult saveResult =
                    saveTouristLodgingPage(
                            result.items()
                    );


            processedCount +=
                    result.items().size();

            insertedCount +=
                    saveResult.insertedCount();

            updatedCount +=
                    saveResult.updatedCount();

            skippedCount +=
                    saveResult.skippedCount();


            int totalPages =
                    calculateTotalPages(
                            result.totalCount(),
                            result.numOfRows()
                    );


            System.out.printf(
                    "[TOURIST_LODGING] page %d / %d, processed=%d/%d, insert=%d, update=%d, skip=%d%n",
                    page,
                    totalPages,
                    processedCount,
                    result.totalCount(),
                    insertedCount,
                    updatedCount,
                    skippedCount
            );


            if (page >= totalPages) {
                break;
            }


            page++;
        }


        return new ImportResult(
                processedCount,
                insertedCount,
                updatedCount,
                skippedCount
        );
    }


    private PageSaveResult saveTouristLodgingPage(
            List<TouristLodgingLicenseClient.Item> items
    ) {

        List<String> managementNumbers =
                items.stream()
                        .map(
                                TouristLodgingLicenseClient.Item
                                        ::managementNumber
                        )
                        .filter(Objects::nonNull)
                        .filter(
                                value ->
                                        !value.isBlank()
                        )
                        .distinct()
                        .toList();


        Map<BusinessKey, AccommodationBusiness>
                existingMap =

                repository
                        .findBySourceAndManagementNumberIn(
                                AccommodationSource
                                        .TOURIST_LODGING,
                                managementNumbers
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(

                                        business ->
                                                new BusinessKey(
                                                        business
                                                                .getLocalGovernmentCode(),
                                                        business
                                                                .getManagementNumber()
                                                ),

                                        Function.identity(),

                                        (first, second) ->
                                                first
                                )
                        );


        List<AccommodationBusiness> saveList =
                new ArrayList<>();


        Set<BusinessKey> pageKeys =
                new HashSet<>();


        int inserted = 0;
        int updated = 0;
        int skipped = 0;


        for (
                TouristLodgingLicenseClient.Item item
                : items
        ) {

            if (isBlank(item.managementNumber())
                    || isBlank(
                    item.localGovernmentCode()
            )
                    || isBlank(
                    item.businessName()
            )) {

                skipped++;
                continue;
            }


            BusinessKey key =
                    new BusinessKey(
                            item.localGovernmentCode(),
                            item.managementNumber()
                    );


            if (!pageKeys.add(key)) {

                skipped++;
                continue;
            }


            AccommodationRegionParser.Region region =
                    regionParser.parse(
                            item.roadAddress(),
                            item.lotAddress()
                    );


            AccommodationBusinessStatus status =
                    resolveStatus(
                            item.salesStatusCode(),
                            item.salesStatusName()
                    );


            AccommodationBusiness existing =
                    existingMap.get(key);


            LocalDateTime incomingUpdatedAt =
                    parseDateTime(
                            item.dataUpdatedAt()
                    );


            if (existing == null) {

                AccommodationBusiness business =
                        new AccommodationBusiness(

                                AccommodationSource
                                        .TOURIST_LODGING,

                                item.localGovernmentCode(),

                                item.managementNumber(),

                                item.businessName(),

                                item.touristLodgingType(),

                                item.salesStatusCode(),

                                status,

                                emptyToNull(
                                        item.roadAddress()
                                ),

                                emptyToNull(
                                        item.lotAddress()
                                ),

                                region.province(),

                                region.city(),

                                region.town(),

                                parseDate(
                                        item.licenseDate()
                                ),

                                parseDate(
                                        item.closedDate()
                                ),

                                incomingUpdatedAt
                        );


                saveList.add(business);

                existingMap.put(
                        key,
                        business
                );

                inserted++;

            } else {

                if (Objects.equals(
                        existing.getSourceUpdatedAt(),
                        incomingUpdatedAt
                )) {

                    skipped++;
                    continue;
                }


                existing.updateLicenseData(

                        item.businessName(),

                        item.touristLodgingType(),

                        item.salesStatusCode(),

                        status,

                        emptyToNull(
                                item.roadAddress()
                        ),

                        emptyToNull(
                                item.lotAddress()
                        ),

                        region.province(),

                        region.city(),

                        region.town(),

                        parseDate(
                                item.licenseDate()
                        ),

                        parseDate(
                                item.closedDate()
                        ),

                        incomingUpdatedAt
                );


                saveList.add(existing);

                updated++;
            }
        }


        if (!saveList.isEmpty()) {
            repository.saveAll(saveList);
        }


        return new PageSaveResult(
                inserted,
                updated,
                skipped
        );
    }


    /*
     * ==========================================
     * 영업상태
     * ==========================================
     */

    private AccommodationBusinessStatus resolveStatus(
            String statusCode,
            String statusName
    ) {

        /*
         * 두 데이터 모두
         * SALS_STTS_CD = 01
         * → 영업/정상
         */
        if ("01".equals(statusCode)) {

            return AccommodationBusinessStatus.OPEN;
        }


        if (statusName == null) {

            return AccommodationBusinessStatus.UNKNOWN;
        }


        String name =
                statusName.trim();


        if (name.contains("폐업")
                || name.contains("말소")
                || name.contains("취소")) {

            return AccommodationBusinessStatus.CLOSED;
        }


        if (name.contains("휴업")
                || name.contains("정지")) {

            return AccommodationBusinessStatus.SUSPENDED;
        }


        if (name.contains("영업")
                || name.contains("정상")) {

            return AccommodationBusinessStatus.OPEN;
        }


        return AccommodationBusinessStatus.UNKNOWN;
    }


    /*
     * ==========================================
     * 날짜
     * ==========================================
     */

    private LocalDate parseDate(
            String value
    ) {

        if (isBlank(value)) {
            return null;
        }


        List<DateTimeFormatter> formatters =
                List.of(

                        DateTimeFormatter
                                .ISO_LOCAL_DATE,

                        DateTimeFormatter
                                .ofPattern(
                                        "yyyyMMdd"
                                )
                );


        for (
                DateTimeFormatter formatter
                : formatters
        ) {

            try {

                return LocalDate.parse(
                        value.trim(),
                        formatter
                );

            } catch (
                    DateTimeParseException ignored
            ) {
            }
        }


        return null;
    }


    private LocalDateTime parseDateTime(
            String value
    ) {

        if (isBlank(value)) {
            return null;
        }


        List<DateTimeFormatter> formatters =
                List.of(

                        DateTimeFormatter
                                .ofPattern(
                                        "yyyy-MM-dd HH:mm:ss"
                                ),

                        DateTimeFormatter
                                .ofPattern(
                                        "yyyyMMddHHmmss"
                                ),

                        DateTimeFormatter
                                .ISO_LOCAL_DATE_TIME
                );


        for (
                DateTimeFormatter formatter
                : formatters
        ) {

            try {

                return LocalDateTime.parse(
                        value.trim(),
                        formatter
                );

            } catch (
                    DateTimeParseException ignored
            ) {
            }
        }


        return null;
    }


    /*
     * ==========================================
     * Utils
     * ==========================================
     */

    private int calculateTotalPages(
            int totalCount,
            int pageSize
    ) {

        if (pageSize <= 0) {
            return 0;
        }


        return (int) Math.ceil(
                (double) totalCount
                        / pageSize
        );
    }


    private boolean isBlank(
            String value
    ) {

        return value == null
                || value.isBlank();
    }


    private String emptyToNull(
            String value
    ) {

        if (isBlank(value)) {
            return null;
        }


        return value.trim();
    }


    /*
     * ==========================================
     * Composite Key
     * ==========================================
     */

    private record BusinessKey(
            String localGovernmentCode,
            String managementNumber
    ) {
    }


    /*
     * ==========================================
     * Result
     * ==========================================
     */

    public record BootstrapResult(
            ImportResult lodging,
            ImportResult touristLodging,
            int totalProcessedCount
    ) {
    }


    public record ImportResult(
            int processedCount,
            int insertedCount,
            int updatedCount,
            int skippedCount
    ) {
    }


    private record PageSaveResult(
            int insertedCount,
            int updatedCount,
            int skippedCount
    ) {
    }
}