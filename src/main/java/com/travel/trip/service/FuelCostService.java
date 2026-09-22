package com.travel.trip.service;

import com.travel.external.opinet.OpinetFuelPriceClient;
import com.travel.trip.entity.VehicleFuelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FuelCostService {

    private final OpinetFuelPriceClient opinetFuelPriceClient;

    @Value("${external.opinet.fallback-price.gasoline:1750}")
    private double fallbackGasolinePrice;

    @Value("${external.opinet.fallback-price.diesel:1650}")
    private double fallbackDieselPrice;

    @Value("${external.opinet.fallback-price.lpg:1100}")
    private double fallbackLpgPrice;

    public long calculateFuelCost(
            VehicleFuelType fuelType,
            Double vehicleEfficiencyKmpl,
            double distanceKm
    ) {
        if (distanceKm <= 0) {
            return 0L;
        }

        VehicleFuelType resolvedFuelType = fuelType == null
                ? VehicleFuelType.GASOLINE
                : fuelType;

        if (resolvedFuelType == VehicleFuelType.ELECTRIC) {
            return 0L;
        }

        double efficiency = vehicleEfficiencyKmpl != null
                && vehicleEfficiencyKmpl > 0
                ? vehicleEfficiencyKmpl
                : 10.0;

        double pricePerLiter = resolvePricePerLiter(resolvedFuelType);
        double liters = distanceKm / efficiency;

        return Math.max(0L, Math.round(liters * pricePerLiter));
    }

    public double getCurrentPricePerLiter(VehicleFuelType fuelType) {
        VehicleFuelType resolved = fuelType == null
                ? VehicleFuelType.GASOLINE
                : fuelType;
        if (resolved == VehicleFuelType.ELECTRIC) {
            return 0.0;
        }
        return resolvePricePerLiter(resolved);
    }

    private double resolvePricePerLiter(VehicleFuelType fuelType) {
        try {
            Double price = opinetFuelPriceClient.getNationalAveragePricePerLiter(fuelType);
            if (price != null && price > 0) {
                return price;
            }
        } catch (RuntimeException e) {
            log.warn(
                    "오피넷 유가 조회 실패. fallback 단가를 사용합니다. fuelType={}, reason={}",
                    fuelType,
                    e.getMessage()
            );
        }

        return switch (fuelType) {
            case DIESEL -> fallbackDieselPrice;
            case LPG -> fallbackLpgPrice;
            case GASOLINE -> fallbackGasolinePrice;
            case ELECTRIC -> 0.0;
        };
    }
}
