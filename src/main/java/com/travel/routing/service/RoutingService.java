package com.travel.routing.service;

import com.travel.external.route.KakaoMobilityDirectionsClient;
import com.travel.routing.dto.DrivingRouteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoutingService {

    private final KakaoMobilityDirectionsClient
            kakaoMobilityDirectionsClient;

    public DrivingRouteResult findDrivingRoute(
            Double originLatitude,
            Double originLongitude,
            Double destinationLatitude,
            Double destinationLongitude
    ) {
        return kakaoMobilityDirectionsClient.findRoute(
                originLatitude,
                originLongitude,
                destinationLatitude,
                destinationLongitude
        );
    }
}
