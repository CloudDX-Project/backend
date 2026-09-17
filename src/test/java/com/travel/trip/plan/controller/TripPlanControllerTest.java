package com.travel.trip.plan.controller;

import com.travel.trip.plan.async.TripPlanGenerationStatus;
import com.travel.trip.plan.async.TripPlanRequestService;
import com.travel.trip.plan.dto.TripPlanRequestResponse;
import com.travel.trip.plan.dto.TripPlanStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripPlanControllerTest {

    @Test
    void acceptsGenerationRequestAndExposesStatusEndpoint() {
        TripPlanRequestService service = mock(TripPlanRequestService.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(2L);

        TripPlanRequestResponse accepted = new TripPlanRequestResponse(
                3L,
                "request-1",
                TripPlanGenerationStatus.PENDING
        );
        when(service.requestPlan(2L, 3L)).thenReturn(accepted);

        TripPlanStatusResponse status = new TripPlanStatusResponse(
                3L,
                "request-1",
                TripPlanGenerationStatus.PROCESSING,
                null,
                null,
                null,
                null,
                null
        );
        when(service.getStatus(2L, 3L)).thenReturn(status);

        TripPlanController controller = new TripPlanController(service);
        var response = controller.createPlan(authentication, 3L);
        var statusResponse = controller.getPlanStatus(authentication, 3L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(accepted);
        assertThat(statusResponse.getData()).isEqualTo(status);
        verify(service).requestPlan(2L, 3L);
        verify(service).getStatus(2L, 3L);
    }
}
