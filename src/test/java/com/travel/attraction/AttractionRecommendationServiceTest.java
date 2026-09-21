package com.travel.attraction;

import com.travel.attraction.data.TouristAttractionData;
import com.travel.attraction.dto.AttractionRecommendRequest;
import com.travel.attraction.repository.TouristAttractionRepository;
import com.travel.external.bedrock.BedrockClient;
import com.travel.trip.entity.TripPace;
import com.travel.trip.entity.TripPreference;
import com.travel.weather.WeatherCondition;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class AttractionRecommendationServiceTest {

    @Test
    void recommendsRepositoryCandidatesWithoutCallingBedrock() {
        TouristAttractionRepository repository = mock(TouristAttractionRepository.class);
        TouristAttractionData attraction = mock(TouristAttractionData.class);
        when(attraction.id()).thenReturn(1L);
        when(attraction.providerId()).thenReturn("visit-jeju-1");
        when(attraction.name()).thenReturn("한담해변");
        when(attraction.categoryName()).thenReturn("자연 관광지");
        when(attraction.latitude()).thenReturn(33.4590);
        when(attraction.longitude()).thenReturn(126.3100);
        when(attraction.tags()).thenReturn("해변 바다 산책");
        when(attraction.allTags()).thenReturn("제주 자연 힐링");
        when(attraction.introduction()).thenReturn("바다를 따라 걷는 야외 관광지");
        when(attraction.representativeImageUrl()).thenReturn("https://example.com/handam.jpg");
        when(repository.findAllRecommendable()).thenReturn(List.of(attraction));

        BedrockClient bedrockClient = mock(BedrockClient.class);
        when(bedrockClient.converse(anyString())).thenReturn(
                "{\"recommendations\":[{\"providerId\":\"visit-jeju-1\",\"aiScore\":91,\"reason\":\"자연 경관이 좋습니다.\"}]}"
        );
        AttractionRecommendationService service = new AttractionRecommendationService(
                repository,
                bedrockClient,
                JsonMapper.builder().findAndAddModules().build()
        );
        ReflectionTestUtils.setField(service, "bedrockRerankEnabled", true);

        var response = service.recommend(new AttractionRecommendRequest(
                33.4500,
                126.3000,
                List.of(TripPreference.NATURE),
                WeatherCondition.SUNNY,
                TripPace.BALANCED,
                1
        ));

        assertThat(response.attractions()).hasSize(1);
        assertThat(response.attractions().getFirst().name()).isEqualTo("한담해변");
        assertThat(response.attractions().getFirst().distanceKm()).isNotNegative();
    }

    @Test
    void returnsEmptyResponseWhenRepositoryHasNoCandidates() {
        TouristAttractionRepository repository = mock(TouristAttractionRepository.class);
        when(repository.findAllRecommendable()).thenReturn(List.of());
        AttractionRecommendationService service = new AttractionRecommendationService(
                repository, mock(BedrockClient.class), JsonMapper.builder().build());

        var response = service.recommend(new AttractionRecommendRequest(
                33.45, 126.30, null, null, null, null));

        assertThat(response.attractions()).isEmpty();
        assertThat(response.requestedLimit()).isEqualTo(12);
    }

    @Test
    void filtersAdministrativeOrganizationsFromTouristRecommendations() {
        TouristAttractionRepository repository = mock(TouristAttractionRepository.class);
        TouristAttractionData landmark = attraction("성산일출봉", "성산일출봉 자연 유산", "일출 오름 유네스코");
        TouristAttractionData organization = attraction("유수암농촌체험휴양마을협의회", "지역 운영 조직", "체험 관광");
        when(repository.findAllRecommendable()).thenReturn(List.of(organization, landmark));

        AttractionRecommendationService service = new AttractionRecommendationService(
                repository, mock(BedrockClient.class), JsonMapper.builder().build());

        var response = service.recommend(new AttractionRecommendRequest(
                33.45, 126.30, List.of(TripPreference.SIGHTSEEING),
                WeatherCondition.SUNNY, TripPace.BALANCED, 10));

        assertThat(response.attractions()).extracting("name")
                .containsExactly("성산일출봉");
    }

    private TouristAttractionData attraction(String name, String introduction, String tags) {
        TouristAttractionData attraction = mock(TouristAttractionData.class);
        when(attraction.id()).thenReturn((long) Math.abs(name.hashCode()));
        when(attraction.providerId()).thenReturn("visit-jeju-" + Math.abs(name.hashCode()));
        when(attraction.name()).thenReturn(name);
        when(attraction.categoryName()).thenReturn("관광지");
        when(attraction.latitude()).thenReturn(33.45);
        when(attraction.longitude()).thenReturn(126.30);
        when(attraction.introduction()).thenReturn(introduction);
        when(attraction.tags()).thenReturn(tags);
        when(attraction.allTags()).thenReturn(tags);
        when(attraction.representativeImageUrl()).thenReturn("https://example.com/photo.jpg");
        return attraction;
    }
}
