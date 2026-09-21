package com.travel.trip.plan.edit;

import com.travel.attraction.repository.TouristAttractionRepository;
import com.travel.cafe.repository.CafeRepository;
import com.travel.restaurant.repository.RestaurantRepository;
import com.travel.trip.plan.type.TripPlanItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanPlaceServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private PlanPlaceService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new PlanPlaceService(
                mock(TouristAttractionRepository.class),
                mock(RestaurantRepository.class),
                mock(CafeRepository.class),
                jdbc
        );
    }

    @Test
    void searchesEachAllowedPlaceTypeWithFixedSql() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());

        assertThat(service.search(TripPlanItemType.ATTRACTION, "애월")).isEmpty();
        assertThat(service.search(TripPlanItemType.RESTAURANT, "흑돼지")).isEmpty();
        assertThat(service.search(TripPlanItemType.CAFE, "카페")).isEmpty();

        verify(jdbc).query(
                org.mockito.ArgumentMatchers.contains("FROM tourist_attraction"),
                anyMap(), any(RowMapper.class));
        verify(jdbc).query(
                org.mockito.ArgumentMatchers.contains("FROM restaurants"),
                anyMap(), any(RowMapper.class));
        verify(jdbc).query(
                org.mockito.ArgumentMatchers.contains("FROM cafes"),
                anyMap(), any(RowMapper.class));
    }

    @Test
    void validatesQueryTypeAndCoordinates() {
        assertThatThrownBy(() -> service.search(TripPlanItemType.CAFE, "a"))
                .isInstanceOf(PlanEditException.class);
        assertThatThrownBy(() -> service.search(TripPlanItemType.ACCOMMODATION, "애월"))
                .isInstanceOf(PlanEditException.class);
        assertThat(PlanPlaceService.validCoordinates(33.5, 126.5)).isTrue();
        assertThat(PlanPlaceService.validCoordinates(null, 126.5)).isFalse();
        assertThat(PlanPlaceService.validCoordinates(91.0, 126.5)).isFalse();
    }
}
