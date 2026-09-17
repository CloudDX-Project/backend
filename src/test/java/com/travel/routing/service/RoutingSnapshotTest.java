package com.travel.routing.service;

import com.travel.external.route.KakaoMobilityDirectionsClient;
import com.travel.routing.dto.DrivingRouteResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class RoutingSnapshotTest {
    @Test void reusesSuccessWithinAPlanAndClearsSnapshotAfterwards() {
        KakaoMobilityDirectionsClient client = mock(KakaoMobilityDirectionsClient.class);
        when(client.findRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DrivingRouteResult(1000, 120, 0, 0, List.of()));
        RoutingService service = new RoutingService(client);
        service.withSnapshot(() -> {
            assertThat(route(service)).isSameAs(route(service));
            return null;
        });
        verify(client, times(1)).findRoute(33.5, 126.5, 33.6, 126.6);
        service.withSnapshot(() -> route(service));
        verify(client, times(2)).findRoute(33.5, 126.5, 33.6, 126.6);
    }

    @Test void reusesFailureWithinAPlanWithoutLeakingItIntoTheNextPlan() {
        KakaoMobilityDirectionsClient client = mock(KakaoMobilityDirectionsClient.class);
        when(client.findRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalStateException("quota"));
        RoutingService service = new RoutingService(client);
        assertThatThrownBy(() -> service.withSnapshot(() -> {
            assertThatThrownBy(() -> route(service)).isInstanceOf(IllegalStateException.class);
            return route(service);
        })).isInstanceOf(IllegalStateException.class);
        verify(client, times(1)).findRoute(33.5, 126.5, 33.6, 126.6);
        assertThatThrownBy(() -> service.withSnapshot(() -> route(service))).isInstanceOf(IllegalStateException.class);
        verify(client, times(2)).findRoute(33.5, 126.5, 33.6, 126.6);
    }
    private DrivingRouteResult route(RoutingService service) {
        return service.findDrivingRoute(33.5, 126.5, 33.6, 126.6);
    }
}
