package com.bbcc.kidly.boundedcontext.chatbot.out.persistence;

import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeChunkRepository;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaKnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long>, KnowledgeChunkRepository {

    @Override
    @Modifying
    @Query("delete from KnowledgeChunk c where c.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
