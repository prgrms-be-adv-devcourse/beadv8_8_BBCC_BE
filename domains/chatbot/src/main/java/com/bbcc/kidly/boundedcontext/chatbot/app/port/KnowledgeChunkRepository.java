package com.bbcc.kidly.boundedcontext.chatbot.app.port;

import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeChunk;
import java.util.List;

public interface KnowledgeChunkRepository {

    <S extends KnowledgeChunk> List<S> saveAll(Iterable<S> chunks);

    void deleteByDocumentId(Long documentId);

    long countByDocumentId(Long documentId);

    /**
     * 색인에 넣을 모든 조각과 그 문서 제목.
     */
    List<IndexedChunk> findAllForIndex();

    /**
     * 조각 묶음이 바뀌었는지 보는 값. ID가 늘기만 하므로 추가 · 교체는 최대 ID가, 삭제만 있으면 개수가 바뀐다.
     */
    ChunkSetStats findStats();

    interface IndexedChunk {

        Long getId();

        Long getDocumentId();

        String getTitle();

        String getContent();

        float[] getEmbedding();
    }

    interface ChunkSetStats {

        long getCount();

        Long getMaxId();
    }
}
