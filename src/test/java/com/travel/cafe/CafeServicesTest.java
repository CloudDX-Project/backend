package com.travel.cafe;

import com.travel.cafe.data.CafeData;
import com.travel.cafe.dto.CafeRecommendRequest;
import com.travel.cafe.dto.CafeSearchRequest;
import com.travel.cafe.repository.CafeRepository;
import com.travel.external.bedrock.BedrockClient;
import com.travel.trip.entity.TripPreference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CafeServicesTest {

    @Test
    void candidatePoolScoresNearbyCafe() {
        CafeRepository repository = mock(CafeRepository.class);
        CafeData cafe = cafe(1L, "애월 오션뷰 카페", 33.46, 126.31);
        when(repository.findAllLocated()).thenReturn(List.of(cafe));

        var pool = new CafeCandidateService(repository).getCandidatePool(
                "test", 33.45, 126.30, true, 1);

        assertThat(pool.candidates()).hasSize(1);
        assertThat(pool.candidates().getFirst().baseScore()).isBetween(0.0, 1.0);
        assertThat(pool.candidates().getFirst().distanceKm()).isNotNegative();
    }

    @Test
    void recommendationUsesCachedCandidatePoolAndBackendScore() {
        CafeCandidateService candidateService = mock(CafeCandidateService.class);
        CafeRepository repository = mock(CafeRepository.class);
        CafeData cafe = cafe(2L, "제주 디저트 카페", 33.46, 126.31);
        var scored = new CafeCandidateService.CafeScoredCandidate(
                cafe, 1.2, 3, 4.5, 0.9, 0.9, 1.0, 0.93);
        when(candidateService.getCandidatePool(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn(new CafeCandidateService.CafeCandidatePool(15, List.of(scored)));
        when(repository.findMenusByCafeIds(anyList())).thenReturn(Map.of());

        BedrockClient bedrockClient = mock(BedrockClient.class);
        when(bedrockClient.converse(anyString())).thenReturn(
                "{\"recommendations\":[{\"cafeId\":2,\"aiScore\":94,\"reason\":\"제주 특색 디저트가 좋습니다.\"}]}"
        );
        CafeRecommendationService service = new CafeRecommendationService(
                candidateService, repository, bedrockClient,
                JsonMapper.builder().findAndAddModules().build());
        ReflectionTestUtils.setField(service, "bedrockRerankEnabled", true);

        var response = service.recommend(new CafeRecommendRequest(
                33.45, 126.30, Set.of(TripPreference.FOOD), 1));

        assertThat(response.cafes()).hasSize(1);
        assertThat(response.cafes().getFirst().cafeName()).isEqualTo("제주 디저트 카페");
    }

    @Test
    void searchCalculatesDistanceAndMapsCafe() {
        CafeRepository repository = mock(CafeRepository.class);
        CafeData nearbyCafe = cafe(3L, "가까운 카페", 33.451, 126.301);
        when(repository.findAllLocated()).thenReturn(List.of(nearbyCafe));
        when(repository.findMenusByCafeIds(anyList())).thenReturn(Map.of());

        var response = new CafeService(repository).search(
                new CafeSearchRequest(33.45, 126.30, 5));

        assertThat(response.cafes()).hasSize(1);
        assertThat(response.cafes().getFirst().distanceKm()).isNotNegative();
    }

    private CafeData cafe(Long id, String name, double latitude, double longitude) {
        CafeData cafe = mock(CafeData.class);
        when(cafe.id()).thenReturn(id);
        when(cafe.cafeName()).thenReturn(name);
        when(cafe.category()).thenReturn("카페 > 디저트");
        when(cafe.latitude()).thenReturn(latitude);
        when(cafe.longitude()).thenReturn(longitude);
        when(cafe.rating()).thenReturn(4.7);
        when(cafe.reviewCount()).thenReturn(500);
        when(cafe.summary()).thenReturn("제주 감귤 디저트와 오션뷰");
        when(cafe.tags()).thenReturn("베이커리 정원");
        when(cafe.facilities()).thenReturn("주차 가능");
        return cafe;
    }
}
