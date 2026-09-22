package com.travel.trip.service;

import com.travel.external.opinet.OpinetFuelPriceClient;
import com.travel.trip.entity.VehicleFuelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FuelCostServiceTest {

    private OpinetFuelPriceClient opinetFuelPriceClient;
    private FuelCostService service;

    @BeforeEach
    void setUp() {
        opinetFuelPriceClient = mock(OpinetFuelPriceClient.class);
        service = new FuelCostService(opinetFuelPriceClient);
        ReflectionTestUtils.setField(service, "fallbackGasolinePrice", 1750.0);
        ReflectionTestUtils.setField(service, "fallbackDieselPrice", 1650.0);
        ReflectionTestUtils.setField(service, "fallbackLpgPrice", 1100.0);
    }

    @Test
    void calculatesGasolineCostWithOpinetPrice() {
        when(opinetFuelPriceClient.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .thenReturn(1800.0);

        assertThat(service.calculateFuelCost(VehicleFuelType.GASOLINE, 12.0, 120.0))
                .isEqualTo(18_000L);
    }

    @Test
    void usesGasolineAndDefaultEfficiencyWhenInputsAreMissing() {
        when(opinetFuelPriceClient.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .thenReturn(1700.0);

        assertThat(service.calculateFuelCost(null, null, 100.0))
                .isEqualTo(17_000L);
    }

    @Test
    void returnsZeroForNonPositiveDistanceAndElectricVehicle() {
        assertThat(service.calculateFuelCost(VehicleFuelType.GASOLINE, 10.0, 0.0)).isZero();
        assertThat(service.calculateFuelCost(VehicleFuelType.GASOLINE, 10.0, -1.0)).isZero();
        assertThat(service.calculateFuelCost(VehicleFuelType.ELECTRIC, 5.0, 100.0)).isZero();
        assertThat(service.getCurrentPricePerLiter(VehicleFuelType.ELECTRIC)).isZero();
    }

    @Test
    void fallsBackWhenOpinetReturnsNullZeroOrThrows() {
        when(opinetFuelPriceClient.getNationalAveragePricePerLiter(VehicleFuelType.DIESEL))
                .thenReturn(null);
        when(opinetFuelPriceClient.getNationalAveragePricePerLiter(VehicleFuelType.LPG))
                .thenReturn(0.0);
        when(opinetFuelPriceClient.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .thenThrow(new IllegalStateException("temporary failure"));

        assertThat(service.getCurrentPricePerLiter(VehicleFuelType.DIESEL)).isEqualTo(1650.0);
        assertThat(service.getCurrentPricePerLiter(VehicleFuelType.LPG)).isEqualTo(1100.0);
        assertThat(service.getCurrentPricePerLiter(VehicleFuelType.GASOLINE)).isEqualTo(1750.0);
    }

    @Test
    void nullFuelTypeUsesGasolineFallback() {
        when(opinetFuelPriceClient.getNationalAveragePricePerLiter(VehicleFuelType.GASOLINE))
                .thenReturn(null);

        assertThat(service.getCurrentPricePerLiter(null)).isEqualTo(1750.0);
    }
}
