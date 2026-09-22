package com.travel.trip.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleFuelTypeTest {

    @Test
    void mapsOpinetProductCodesAndElectricSupport() {
        assertThat(VehicleFuelType.GASOLINE.getOpinetProductCode()).isEqualTo("B027");
        assertThat(VehicleFuelType.DIESEL.getOpinetProductCode()).isEqualTo("D047");
        assertThat(VehicleFuelType.LPG.getOpinetProductCode()).isEqualTo("K015");
        assertThat(VehicleFuelType.ELECTRIC.getOpinetProductCode()).isNull();

        assertThat(VehicleFuelType.GASOLINE.usesOpinetFuelPrice()).isTrue();
        assertThat(VehicleFuelType.ELECTRIC.usesOpinetFuelPrice()).isFalse();
    }
}
