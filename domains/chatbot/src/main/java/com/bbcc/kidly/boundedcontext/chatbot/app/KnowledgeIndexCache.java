package com.bbcc.kidly.boundedcontext.chatbot.app;

import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeChunkRepository;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeChunkRepository.ChunkSetStats;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeChunkRepository.IndexedChunk;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeIndex;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 모든 조각을 메모리 색인으로 들고 있다가, 찾을 때마다 DB의 조각 개수 · 최대 ID를 보고 바뀌었으면 다시 읽는다.
 * 기준이 DB라 서버가 여러 대여도 다른 서버가 저장한 문서를 다음 질문부터 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexCache {

    private final KnowledgeChunkRepository chunks;

    private volatile Snapshot snapshot;

    public KnowledgeIndex current() {
        ChunkSetStats stats = chunks.findStats();
        Snapshot loaded = snapshot;
        if (loaded != null && loaded.matches(stats)) {
            return loaded.index();
        }
        return reload(stats);
    }

    private synchronized KnowledgeIndex reload(ChunkSetStats stats) {
        Snapshot loaded = snapshot;
        if (loaded != null && loaded.matches(stats)) {
            return loaded.index();
        }
        long started = System.nanoTime();
        List<IndexedChunk> rows = chunks.findAllForIndex();
        // 비교 값은 읽은 행에서 다시 센다. 개수 조회와 행 읽기 사이에 바뀐 것은 다음 질문 때 다시 읽는다
        long maxId = rows.stream().mapToLong(IndexedChunk::getId).max().orElse(0);
        KnowledgeIndex index = KnowledgeIndex.of(rows.stream()
            .map(row -> new KnowledgeIndex.Entry(row.getId(), row.getDocumentId(), row.getTitle(), row.getContent(),
                row.getEmbedding()))
            .toList());
        snapshot = new Snapshot(rows.size(), maxId, index);
        log.info("챗봇 색인을 다시 읽음: chunks={}, tookMs={}", index.size(), (System.nanoTime() - started) / 1_000_000);
        return index;
    }

    private record Snapshot(long count, long maxId, KnowledgeIndex index) {

        boolean matches(ChunkSetStats stats) {
            long statsMaxId = stats.getMaxId() == null ? 0 : stats.getMaxId();
            return count == stats.getCount() && maxId == statsMaxId;
        }
    }
}
