package com.travel.external.opinet;

import com.sun.net.httpserver.HttpServer;
import com.travel.trip.entity.VehicleFuelType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpinetFuelPriceClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsRequestedFuelPriceFromOpinetJson() throws Exception {
        startServer(200, """
                {"RESULT":{"OIL":[
                  {"PRODCD":"B027","PRICE":"1,789.5"},
                  {"PRODCD":"D047","PRICE":"1,612.3"},
                  {"PRODCD":"K015","PRICE":"1,045.0"}
                ]}}
                """);

        OpinetFuelPriceClient client = new OpinetFuelPriceClient(baseUrl(), "test-key");

        assertThat(client.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .isEqualTo(1789.5);
        assertThat(client.getNationalAveragePricePerLiter(VehicleFuelType.DIESEL))
                .isEqualTo(1612.3);
        assertThat(client.getNationalAveragePricePerLiter(VehicleFuelType.LPG))
                .isEqualTo(1045.0);
    }

    @Test
    void returnsZeroWithoutCallingApiForUnsupportedFuel() {
        OpinetFuelPriceClient client = new OpinetFuelPriceClient("http://127.0.0.1:1", "");

        assertThat(client.getNationalAveragePricePerLiter(null)).isZero();
        assertThat(client.getNationalAveragePricePerLiter(VehicleFuelType.ELECTRIC)).isZero();
    }

    @Test
    void rejectsMissingApiKeyForOpinetFuel() {
        OpinetFuelPriceClient client = new OpinetFuelPriceClient("http://127.0.0.1:1", "   ");

        assertThatThrownBy(() -> client.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPINET_API_KEY");
    }

    @Test
    void rejectsResponseWithoutRequestedProduct() throws Exception {
        startServer(200, """
                {"RESULT":{"OIL":[
                  {"PRODCD":"D047","PRICE":"1,600"},
                  {"PRODCD":"B027","PRICE":"not-a-number"}
                ]}}
                """);

        OpinetFuelPriceClient client = new OpinetFuelPriceClient(baseUrl(), "test-key");

        assertThatThrownBy(() -> client.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("가격을 찾을 수 없습니다");
    }

    @Test
    void rejectsEmptyResponseBody() throws Exception {
        startServer(200, "{}");
        OpinetFuelPriceClient client = new OpinetFuelPriceClient(baseUrl(), "test-key");

        assertThatThrownBy(() -> client.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("응답이 비어");
    }

    @Test
    void convertsHttpErrorsIntoDomainException() throws Exception {
        startServer(500, "{\"error\":\"boom\"}");
        OpinetFuelPriceClient client = new OpinetFuelPriceClient(baseUrl(), "test-key");

        assertThatThrownBy(() -> client.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void rejectsMissingOilArrayAndConnectionFailure() throws Exception {
        startServer(200, "{\"RESULT\":{}}");
        OpinetFuelPriceClient client = new OpinetFuelPriceClient(baseUrl(), "test-key");

        assertThatThrownBy(() -> client.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("응답이 비어");

        server.stop(0);
        server = null;
        OpinetFuelPriceClient disconnected = new OpinetFuelPriceClient("http://127.0.0.1:1", "test-key");
        assertThatThrownBy(() -> disconnected.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API 연결");
    }

    @Test
    void skipsBlankAndNegativePricesBeforeFindingValidValue() throws Exception {
        startServer(200, """
                {"RESULT":{"OIL":[
                  {"PRODCD":"B027","PRICE":" "},
                  {"PRODCD":"B027","PRICE":"-1"},
                  {"PRODCD":"B027","PRICE":"1,800"}
                ]}}
                """);

        OpinetFuelPriceClient client = new OpinetFuelPriceClient(baseUrl(), "test-key");
        assertThat(client.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .isEqualTo(1800.0);
    }

    private void startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/avgAllPrice.do", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
