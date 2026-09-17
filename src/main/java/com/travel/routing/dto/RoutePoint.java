package com.travel.routing.dto;

import java.io.Serializable;

public record RoutePoint(
        Double latitude,
        Double longitude
) implements Serializable {
}
