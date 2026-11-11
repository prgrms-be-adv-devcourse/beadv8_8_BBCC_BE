package com.bbcc.kidly.boundedcontext.chatbot.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 문서 저장 · 수정. category는 FAQ · POLICY · SIZE_GUIDE.
 */
public record KnowledgeDocumentRequest(
    @NotBlank @Size(max = 200) String title,
    @NotNull String category,
    @NotBlank @Size(max = 20_000) String content) {
}
