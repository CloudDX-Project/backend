package com.travel.trip.entity;

public enum VehicleFuelType {
    GASOLINE("B027"),
    DIESEL("D047"),
    LPG("K015"),
    ELECTRIC(null);

    private final String opinetProductCode;

    VehicleFuelType(String opinetProductCode) {
        this.opinetProductCode = opinetProductCode;
    }

    public String getOpinetProductCode() {
        return opinetProductCode;
    }

    public boolean usesOpinetFuelPrice() {
        return opinetProductCode != null;
    }
}
