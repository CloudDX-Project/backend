package com.travel.external.opinet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.travel.trip.entity.VehicleFuelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class OpinetFuelPriceClient {

    private final RestClient restClient;
    private final String apiKey;

    public OpinetFuelPriceClient(
            @Value("${external.opinet.base-url:https://www.opinet.co.kr}")
            String baseUrl,
            @Value("${external.opinet.api-key:}")
            String apiKey
    ) {
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory())
                .baseUrl(baseUrl)
                .build();
        this.apiKey = apiKey;
    }

    @Cacheable(
            value = "opinetFuelPrice",
            key = "#fuelType.name()",
            sync = true
    )
    public Double getNationalAveragePricePerLiter(
            VehicleFuelType fuelType
    ) {
        if (fuelType == null || !fuelType.usesOpinetFuelPrice()) {
            return 0.0;
        }

        validateApiKey();

        try {
            OpinetAveragePriceResponse response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/avgAllPrice.do")
                            .queryParam("out", "json")
                            .queryParam("certkey", apiKey)
                            .build())
                    .retrieve()
                    .body(OpinetAveragePriceResponse.class);

            Double price = extractPrice(response, fuelType.getOpinetProductCode());
            log.info(
                    "오피넷 전국 평균 유가 조회 성공. fuelType={}, pricePerLiter={}",
                    fuelType,
                    price
            );
            return price;

        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "오피넷 전국 평균 유가 조회에 실패했습니다. HTTP "
                            + e.getStatusCode().value(),
                    e
            );
        } catch (RestClientException e) {
            throw new IllegalStateException(
                    "오피넷 전국 평균 유가 API 연결에 실패했습니다.",
                    e
            );
        }
    }

    private Double extractPrice(
            OpinetAveragePriceResponse response,
            String productCode
    ) {
        if (response == null
                || response.result() == null
                || response.result().oil() == null) {
            throw new IllegalStateException("오피넷 평균 유가 응답이 비어 있습니다.");
        }

        return response.result().oil().stream()
                .filter(item -> item != null && productCode.equals(item.productCode()))
                .map(OpinetOilPrice::price)
                .map(this::parsePositivePrice)
                .filter(price -> price > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "오피넷 응답에서 요청 연료 가격을 찾을 수 없습니다: " + productCode
                ));
    }

    private Double parsePositivePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(rawPrice.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPINET_API_KEY가 설정되어 있지 않습니다."
            );
        }
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .build()
        );
        factory.setReadTimeout(Duration.ofSeconds(6));
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpinetAveragePriceResponse(
            @JsonProperty("RESULT") OpinetResult result
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpinetResult(
            @JsonProperty("OIL") List<OpinetOilPrice> oil
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpinetOilPrice(
            @JsonProperty("PRODCD") String productCode,
            @JsonProperty("PRICE") String price
    ) {
    }
}
