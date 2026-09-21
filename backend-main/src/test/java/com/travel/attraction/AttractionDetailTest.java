package com.travel.attraction;

import com.travel.attraction.data.TouristAttractionData;
import com.travel.attraction.dto.AttractionDetailResponse;
import com.travel.attraction.repository.TouristAttractionRepository;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttractionDetailTest {
    @Test void returnsDatabaseDetailsAndPreservesUnknownFieldsAsNull() {
        TouristAttractionRepository repository = mock(TouristAttractionRepository.class);
        TouristAttractionData data = mock(TouristAttractionData.class);
        when(data.id()).thenReturn(42L);
        when(data.name()).thenReturn("한림바다체험마을");
        when(data.introduction()).thenReturn("바다 체험 관광지입니다.");
        when(data.latitude()).thenReturn(33.4);
        when(data.longitude()).thenReturn(126.3);
        when(repository.findById(42L)).thenReturn(Optional.of(data));
        AttractionDetailResponse response = new AttractionService(repository).getDetail(42L);
        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.name()).isEqualTo("한림바다체험마을");
        assertThat(response.introduction()).isEqualTo("바다 체험 관광지입니다.");
        assertThat(response.phoneNumber()).isNull();
        assertThat(response.latitude()).isEqualTo(33.4);
    }

    @Test void missingIdUsesNotFoundError() {
        TouristAttractionRepository repository = mock(TouristAttractionRepository.class);
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new AttractionService(repository).getDetail(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ATTRACTION_NOT_FOUND));
    }

    @Test void invalidIdsUseBadRequestWithoutQueryingRepository() {
        TouristAttractionRepository repository = mock(TouristAttractionRepository.class);
        AttractionService service = new AttractionService(repository);
        AttractionController controller = new AttractionController(service, null);
        for (String id : new String[]{"0", "-1", "abc", "9223372036854775808"}) {
            assertThatThrownBy(() -> controller.getDetail(id))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        }
        verifyNoInteractions(repository);
    }
}
