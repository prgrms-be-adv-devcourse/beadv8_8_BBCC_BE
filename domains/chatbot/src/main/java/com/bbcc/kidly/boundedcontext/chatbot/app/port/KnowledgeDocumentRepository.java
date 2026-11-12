package com.bbcc.kidly.boundedcontext.chatbot.app.port;

import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeDocument;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface KnowledgeDocumentRepository {

    KnowledgeDocument save(KnowledgeDocument document);

    KnowledgeDocument saveAndFlush(KnowledgeDocument document);

    Optional<KnowledgeDocument> findById(Long id);

    /**
     * 행을 잠그고 읽는다. 같은 문서의 수정 · 삭제가 겹쳐도 조각을 한 벌만 남기려고 쓴다.
     */
    Optional<KnowledgeDocument> findByIdForUpdate(Long id);

    boolean existsById(Long id);

    Page<KnowledgeDocument> findAll(Pageable pageable);

    void delete(KnowledgeDocument document);
}
