package com.bbcc.kidly.boundedcontext.chatbot.app;

import com.bbcc.kidly.boundedcontext.chatbot.app.KnowledgeDocumentWriter.ChunkDraft;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.ChatbotAi;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeChunkRepository;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.KnowledgeDocumentRepository;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeCategory;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeChunker;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeDocument;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 답변 근거 문서 관리. 저장 · 수정하면 본문을 조각내 임베딩하고 문서의 조각을 모두 바꾼다.
 * 임베딩(외부 호출)은 트랜잭션 밖에서 먼저 하고, 반영은 {@link KnowledgeDocumentWriter}가 한 트랜잭션으로 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentService {

    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final KnowledgeDocumentWriter writer;
    private final ChatbotAi ai;

    public DocumentDetail create(String title, String category, String content) {
        KnowledgeCategory parsed = parseCategory(category);
        long started = System.nanoTime();
        List<ChunkDraft> drafts = embedChunks(title, content);
        DocumentDetail created = writer.create(title, parsed, content, drafts);
        log.info("챗봇 문서 저장: documentId={}, chunks={}, tookMs={}", created.id(), drafts.size(), elapsedMillis(started));
        return created;
    }

    public DocumentDetail update(Long id, String title, String category, String content) {
        KnowledgeCategory parsed = parseCategory(category);
        // 없는 문서에 임베딩 비용을 쓰지 않으려고 먼저 본다. 그 사이 지워지면 반영할 때 다시 404다
        if (!documents.existsById(id)) {
            throw new BusinessException(ChatbotErrorCode.DOCUMENT_NOT_FOUND);
        }
        long started = System.nanoTime();
        List<ChunkDraft> drafts = embedChunks(title, content);
        DocumentDetail updated = writer.replace(id, title, parsed, content, drafts);
        log.info("챗봇 문서 수정: documentId={}, chunks={}, tookMs={}", id, drafts.size(), elapsedMillis(started));
        return updated;
    }

    public void delete(Long id) {
        writer.delete(id);
        log.info("챗봇 문서 삭제: documentId={}", id);
    }

    @Transactional(readOnly = true)
    public Page<DocumentSummary> list(int page, int size) {
        return documents.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
            .map(DocumentSummary::from);
    }

    @Transactional(readOnly = true)
    public DocumentDetail get(Long id) {
        KnowledgeDocument document = documents.findById(id)
            .orElseThrow(() -> new BusinessException(ChatbotErrorCode.DOCUMENT_NOT_FOUND));
        return DocumentDetail.from(document, chunks.countByDocumentId(id));
    }

    // 조각만으로는 어느 문서 이야기인지 흐려져, 임베딩할 글에는 문서 제목을 앞에 붙인다(저장하는 조각 본문에는 붙이지 않는다)
    private List<ChunkDraft> embedChunks(String title, String content) {
        List<String> pieces = KnowledgeChunker.split(content);
        List<float[]> vectors;
        try {
            vectors = ai.embed(pieces.stream().map(piece -> title + "\n\n" + piece).toList());
        } catch (ChatbotAi.UnavailableException e) {
            log.warn("챗봇 문서 임베딩 실패: chunks={}, cause={}", pieces.size(), e.causeName());
            throw new BusinessException(ChatbotErrorCode.AI_UNAVAILABLE);
        }
        List<ChunkDraft> drafts = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            drafts.add(new ChunkDraft(pieces.get(i), vectors.get(i)));
        }
        return drafts;
    }

    private static KnowledgeCategory parseCategory(String category) {
        try {
            return KnowledgeCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ChatbotErrorCode.INVALID_CATEGORY);
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    public record DocumentSummary(Long id, String title, String category, LocalDateTime createdAt,
                                  LocalDateTime updatedAt) {

        static DocumentSummary from(KnowledgeDocument document) {
            return new DocumentSummary(document.getId(), document.getTitle(), document.getCategory().name(),
                document.getCreatedAt(), document.getUpdatedAt());
        }
    }

    public record DocumentDetail(Long id, String title, String category, String content, long chunkCount,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {

        static DocumentDetail from(KnowledgeDocument document, long chunkCount) {
            return new DocumentDetail(document.getId(), document.getTitle(), document.getCategory().name(),
                document.getContent(), chunkCount, document.getCreatedAt(), document.getUpdatedAt());
        }
    }
}
