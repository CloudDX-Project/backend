package com.travel.external.accommodation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class LodgingLicenseClient {

    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final String path;
    private final String serviceKey;

    public LodgingLicenseClient(
            @Value("${external.accommodation.base-url}") String baseUrl,
            @Value("${external.accommodation.lodging.path}") String path,
            @Value("${external.accommodation.lodging.service-key}") String serviceKey
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.path = path;
        this.serviceKey = serviceKey;
    }

    public Page getPage(int pageNo) {

        ApiResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("pageNo", pageNo)
                        .queryParam("numOfRows", PAGE_SIZE)
                        .queryParam("returnType", "json")
                        .build())
                .retrieve()
                .body(ApiResponse.class);

        if (response == null
                || response.response() == null
                || response.response().body() == null) {

            return new Page(
                    List.of(),
                    pageNo,
                    PAGE_SIZE,
                    0
            );
        }

        ResponseBody body = response.response().body();

        List<Item> items =
                body.items() != null && body.items().item() != null
                        ? body.items().item()
                        : List.of();

        return new Page(
                items,
                body.pageNo(),
                body.numOfRows(),
                body.totalCount()
        );
    }

    public record Page(
            List<Item> items,
            int pageNo,
            int numOfRows,
            int totalCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiResponse(
            Response response
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Response(
            ResponseBody body,
            Header header
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Header(
            String resultCode,
            String resultMsg
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseBody(
            Items items,
            int numOfRows,
            int pageNo,
            int totalCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Items(
            List<Item> item
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(

            @JsonProperty("MNG_NO")
            String managementNumber,

            @JsonProperty("BPLC_NM")
            String businessName,

            @JsonProperty("SALS_STTS_CD")
            String salesStatusCode,

            @JsonProperty("SALS_STTS_NM")
            String salesStatusName,

            @JsonProperty("DTL_SALS_STTS_CD")
            String detailStatusCode,

            @JsonProperty("DTL_SALS_STTS_NM")
            String detailStatusName,

            @JsonProperty("ROAD_NM_ADDR")
            String roadAddress,

            @JsonProperty("LOTNO_ADDR")
            String lotAddress,

            @JsonProperty("LCPMT_YMD")
            String licenseDate,

            @JsonProperty("CLSBIZ_YMD")
            String closedDate,

            @JsonProperty("DAT_UPDT_PNT")
            String dataUpdatedAt,

            @JsonProperty("OPN_ATMY_GRP_CD")
            String localGovernmentCode,

            @JsonProperty("SNTTN_BZSTAT_NM")
            String businessType,

            @JsonProperty("TELNO")
            String telephone,

            @JsonProperty("KSRM_CNT")
            String koreanRoomCount,

            @JsonProperty("WSRM_CNT")
            String westernRoomCount

    ) {
    }
}