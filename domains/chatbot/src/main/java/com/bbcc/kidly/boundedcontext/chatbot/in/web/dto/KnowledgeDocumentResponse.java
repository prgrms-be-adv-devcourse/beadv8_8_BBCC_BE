package com.bbcc.kidly.boundedcontext.chatbot.in.web.dto;

import com.bbcc.kidly.boundedcontext.chatbot.app.KnowledgeDocumentService.DocumentDetail;
import java.time.LocalDateTime;

public record KnowledgeDocumentResponse(Long id, String title, String category, String content, long chunkCount,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static KnowledgeDocumentResponse from(DocumentDetail detail) {
        return new KnowledgeDocumentResponse(detail.id(), detail.title(), detail.category(), detail.content(),
            detail.chunkCount(), detail.createdAt(), detail.updatedAt());
    }
}
