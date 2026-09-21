package com.travel.trip.plan.edit;

import org.springframework.http.HttpStatus;

public class PlanEditException extends RuntimeException {
    private final HttpStatus status;

    public PlanEditException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }

    public static PlanEditException invalid(String message) {
        return new PlanEditException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
