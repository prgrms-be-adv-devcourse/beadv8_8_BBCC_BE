package com.bbcc.kidly.boundedcontext.chatbot.in.web.dto;

import com.bbcc.kidly.boundedcontext.chatbot.app.KnowledgeDocumentService.DocumentSummary;
import java.time.LocalDateTime;

public record KnowledgeDocumentSummaryResponse(Long id, String title, String category, LocalDateTime createdAt,
                                               LocalDateTime updatedAt) {

    public static KnowledgeDocumentSummaryResponse from(DocumentSummary summary) {
        return new KnowledgeDocumentSummaryResponse(summary.id(), summary.title(), summary.category(),
            summary.createdAt(), summary.updatedAt());
    }
}
