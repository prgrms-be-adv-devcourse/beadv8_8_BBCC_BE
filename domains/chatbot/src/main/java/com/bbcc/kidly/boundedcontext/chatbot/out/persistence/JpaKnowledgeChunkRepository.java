package com.bbcc.kidly.boundedcontext.chatbot.out.persistence;

import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeChunkRepository;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaKnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long>, KnowledgeChunkRepository {

    @Override
    @Modifying
    @Query("delete from KnowledgeChunk c where c.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    @Override
    @Query("""
        select c.id as id, c.documentId as documentId, d.title as title, c.content as content, c.embedding as embedding
        from KnowledgeChunk c join KnowledgeDocument d on d.id = c.documentId
        order by c.id
        """)
    List<IndexedChunk> findAllForIndex();

    @Override
    @Query("select count(c) as count, max(c.id) as maxId from KnowledgeChunk c")
    ChunkSetStats findStats();
}
