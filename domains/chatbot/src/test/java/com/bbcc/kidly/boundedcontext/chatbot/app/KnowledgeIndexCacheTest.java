package com.bbcc.kidly.boundedcontext.chatbot.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeChunkRepository;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeChunkRepository.ChunkSetStats;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeChunkRepository.IndexedChunk;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnowledgeIndexCacheTest {

    private final KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
    private final KnowledgeIndexCache cache = new KnowledgeIndexCache(chunks);

    @Test
    @DisplayName("조각 개수 · 최대 ID가 그대로면 다시 읽지 않는다")
    void reusesWhileUnchanged() {
        given(chunks.findStats()).willReturn(stats(1, 5L));
        given(chunks.findAllForIndex()).willReturn(List.of(chunk(5)));

        cache.current();
        cache.current();

        verify(chunks, times(1)).findAllForIndex();
    }

    @Test
    @DisplayName("조각이 바뀌면(추가 · 교체는 최대 ID, 삭제는 개수) 다시 읽는다")
    void reloadsWhenChanged() {
        given(chunks.findStats()).willReturn(stats(1, 5L), stats(2, 6L), stats(1, 6L));
        given(chunks.findAllForIndex()).willReturn(List.of(chunk(5)), List.of(chunk(5), chunk(6)), List.of(chunk(6)));

        assertThat(cache.current().size()).isEqualTo(1);
        assertThat(cache.current().size()).isEqualTo(2);
        assertThat(cache.current().size()).isEqualTo(1);
        verify(chunks, times(3)).findAllForIndex();
    }

    @Test
    @DisplayName("조각이 없으면 빈 색인이다")
    void emptyWithoutChunks() {
        given(chunks.findStats()).willReturn(stats(0, null));
        given(chunks.findAllForIndex()).willReturn(List.of());

        assertThat(cache.current().isEmpty()).isTrue();
        assertThat(cache.current().isEmpty()).isTrue();
        verify(chunks, times(1)).findAllForIndex();
    }

    private static ChunkSetStats stats(long count, Long maxId) {
        return new ChunkSetStats() {
            @Override
            public long getCount() {
                return count;
            }

            @Override
            public Long getMaxId() {
                return maxId;
            }
        };
    }

    private static IndexedChunk chunk(long id) {
        return new IndexedChunk() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Long getDocumentId() {
                return 1L;
            }

            @Override
            public String getTitle() {
                return "제목";
            }

            @Override
            public String getContent() {
                return "본문";
            }

            @Override
            public float[] getEmbedding() {
                return new float[] {1, 0};
            }
        };
    }
}
