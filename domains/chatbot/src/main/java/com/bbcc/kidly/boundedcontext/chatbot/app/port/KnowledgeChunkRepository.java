package com.bbcc.kidly.boundedcontext.chatbot.app.port;

import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeChunk;
import java.util.List;

public interface KnowledgeChunkRepository {

    <S extends KnowledgeChunk> List<S> saveAll(Iterable<S> chunks);

    void deleteByDocumentId(Long documentId);

    long countByDocumentId(Long documentId);
}
