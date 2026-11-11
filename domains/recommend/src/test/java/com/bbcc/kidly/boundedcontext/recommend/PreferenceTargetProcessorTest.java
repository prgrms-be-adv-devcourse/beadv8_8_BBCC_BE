package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.PreferenceResult;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.PreferenceTargetProcessor;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.batch.PreferenceTargets;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignalType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreferenceTargetProcessorTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 10, 3, 0);

    @Mock
    private PreferenceTargets targets;

    @Mock
    private PreferenceSourceReader sourceReader;

    @Captor
    private ArgumentCaptor<Collection<Long>> productIds;

    @Test
    @DisplayName("PREF-13 취향 벡터에 넣는 상세 진입 · 담기 상품의 벡터만 한 번에 읽어 그 벡터로 취향 벡터를 만든다")
    void readEmbeddingsOnlyForTasteSignals() {
        given(targets.findSignals(4L, BASE_TIME)).willReturn(List.of(
            signal(PreferenceSignalType.SEARCH, null),
            signal(PreferenceSignalType.PRODUCT_VIEWED, 1L),
            signal(PreferenceSignalType.CART_ITEM_ADDED, 2L),
            signal(PreferenceSignalType.PRODUCT_VIEWED, 1L),
            signal(PreferenceSignalType.PURCHASE_CONFIRMED, 3L)));
        given(sourceReader.findEmbeddings(anyCollection())).willReturn(Map.of(1L, EmbeddingVectorFixture.axis(0)));
        PreferenceTargetProcessor processor =
            new PreferenceTargetProcessor(targets, sourceReader, new PreferenceCalculator(), BASE_TIME);

        PreferenceResult result = processor.process(4L);

        verify(sourceReader).findEmbeddings(productIds.capture());
        assertThat(productIds.getValue()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(result.targetId()).isEqualTo(4L);
        assertThat(result.snapshot().tasteVector().embedding()).isEqualTo(EmbeddingVectorFixture.axis(0));
        assertThat(result.snapshot().calculatedAt()).isEqualTo(BASE_TIME);
    }

    private static PreferenceSignal signal(PreferenceSignalType type, Long productId) {
        return new PreferenceSignal(type, BASE_TIME.minusDays(1), 10L, "브랜드A", productId, false);
    }
}
