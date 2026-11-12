package com.bbcc.kidly.boundedcontext.chatbot.in.web.dto;

import com.bbcc.kidly.boundedcontext.chatbot.app.ChatService.ChatAnswer;
import java.util.List;

public record ChatResponse(String answer, List<SourceResponse> sources) {

    public static ChatResponse from(ChatAnswer answer) {
        return new ChatResponse(answer.answer(), answer.sources().stream()
            .map(source -> new SourceResponse(source.documentId(), source.title()))
            .toList());
    }

    public record SourceResponse(Long documentId, String title) {
    }
}
