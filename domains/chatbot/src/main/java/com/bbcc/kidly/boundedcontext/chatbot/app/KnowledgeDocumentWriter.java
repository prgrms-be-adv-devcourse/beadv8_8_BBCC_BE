package com.bbcc.kidly.boundedcontext.chatbot.app;

import com.bbcc.kidly.boundedcontext.chatbot.app.KnowledgeDocumentService.DocumentDetail;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeChunkRepository;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeDocumentRepository;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeCategory;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeChunk;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeDocument;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문서와 그 조각을 한 트랜잭션으로 바꾼다. 임베딩은 부르는 쪽이 트랜잭션 밖에서 미리 해 온다.
 */
@Component
@RequiredArgsConstructor
class KnowledgeDocumentWriter {

    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;

    @Transactional
    public DocumentDetail create(String title, KnowledgeCategory category, String content, List<ChunkDraft> drafts) {
        KnowledgeDocument document = documents.save(KnowledgeDocument.create(title, category, content));
        chunks.saveAll(toChunks(document.getId(), drafts));
        return DocumentDetail.from(document, drafts.size());
    }

    // 행 잠금 뒤에 조각을 지워, 같은 문서의 수정이 겹쳐도 나중 것의 삭제가 앞 것이 넣은 조각까지 본다
    @Transactional
    public DocumentDetail replace(Long id, String title, KnowledgeCategory category, String content,
        List<ChunkDraft> drafts) {
        KnowledgeDocument document = documents.findByIdForUpdate(id)
            .orElseThrow(() -> new BusinessException(ChatbotErrorCode.DOCUMENT_NOT_FOUND));
        document.revise(title, category, content);
        // 응답에 바뀐 수정 시각을 담으려고 바로 반영한다
        documents.saveAndFlush(document);
        chunks.deleteByDocumentId(id);
        chunks.saveAll(toChunks(id, drafts));
        return DocumentDetail.from(document, drafts.size());
    }

    // 조각은 DB가 함께 지운다(ON DELETE CASCADE)
    @Transactional
    public void delete(Long id) {
        KnowledgeDocument document = documents.findByIdForUpdate(id)
            .orElseThrow(() -> new BusinessException(ChatbotErrorCode.DOCUMENT_NOT_FOUND));
        documents.delete(document);
    }

    private static List<KnowledgeChunk> toChunks(Long documentId, List<ChunkDraft> drafts) {
        List<KnowledgeChunk> result = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            result.add(KnowledgeChunk.of(documentId, i, drafts.get(i).content(), drafts.get(i).embedding()));
        }
        return result;
    }

    record ChunkDraft(String content, float[] embedding) {
    }
}
