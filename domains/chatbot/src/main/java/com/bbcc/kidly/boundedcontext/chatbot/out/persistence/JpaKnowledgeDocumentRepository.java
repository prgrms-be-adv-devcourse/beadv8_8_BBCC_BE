package com.bbcc.kidly.boundedcontext.chatbot.out.persistence;

import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeDocumentRepository;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeDocument;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaKnowledgeDocumentRepository
    extends JpaRepository<KnowledgeDocument, Long>, KnowledgeDocumentRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from KnowledgeDocument d where d.id = :id")
    Optional<KnowledgeDocument> findByIdForUpdate(@Param("id") Long id);
}
