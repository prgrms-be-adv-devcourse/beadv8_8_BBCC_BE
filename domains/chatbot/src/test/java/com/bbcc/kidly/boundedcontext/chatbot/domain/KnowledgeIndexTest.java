package com.bbcc.kidly.boundedcontext.chatbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnowledgeIndexTest {

    @Test
    @DisplayName("코사인 유사도가 높은 순으로 topK개까지 준다")
    void ranksByCosine() {
        KnowledgeIndex index = KnowledgeIndex.of(List.of(
            entry(1, new float[] {1, 0, 0}),
            entry(2, new float[] {0.6f, 0.8f, 0}),
            entry(3, new float[] {10, 1, 0}),
            entry(4, new float[] {0, 0, 1})));

        List<KnowledgeIndex.Match> matches = index.search(new float[] {2, 0, 0}, 2, 0.0);

        assertThat(matches).extracting(match -> match.entry().chunkId()).containsExactly(1L, 3L);
        assertThat(matches.getFirst().score()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("minScore보다 낮은 조각은 넣지 않는다")
    void dropsBelowMinScore() {
        KnowledgeIndex index = KnowledgeIndex.of(List.of(
            entry(1, new float[] {1, 0}),
            entry(2, new float[] {0.6f, 0.8f}),
            entry(3, new float[] {0, 1})));

        List<KnowledgeIndex.Match> matches = index.search(new float[] {1, 0}, 10, 0.5);

        assertThat(matches).extracting(match -> match.entry().chunkId()).containsExactly(1L, 2L);
        assertThat(matches.get(1).score()).isCloseTo(0.6, within(1e-6));
    }

    @Test
    @DisplayName("길이가 0인 벡터와 차원이 다른 벡터는 비교하지 않는다")
    void skipsZeroAndMismatchedVectors() {
        KnowledgeIndex index = KnowledgeIndex.of(List.of(
            entry(1, new float[] {0, 0}),
            entry(2, new float[] {1, 0, 0}),
            entry(3, new float[] {1, 0})));

        assertThat(index.size()).isEqualTo(2);
        assertThat(index.search(new float[] {1, 0}, 10, 0.0))
            .extracting(match -> match.entry().chunkId()).containsExactly(3L);
        assertThat(index.search(new float[] {0, 0}, 10, 0.0)).isEmpty();
    }

    @Test
    @DisplayName("빈 색인은 아무것도 찾지 않는다")
    void emptyIndex() {
        assertThat(KnowledgeIndex.of(List.of()).isEmpty()).isTrue();
        assertThat(KnowledgeIndex.of(List.of()).search(new float[] {1}, 4, 0.0)).isEmpty();
    }

    private static KnowledgeIndex.Entry entry(long chunkId, float[] embedding) {
        return new KnowledgeIndex.Entry(chunkId, 100 + chunkId, "제목" + chunkId, "본문" + chunkId, embedding);
    }
}
