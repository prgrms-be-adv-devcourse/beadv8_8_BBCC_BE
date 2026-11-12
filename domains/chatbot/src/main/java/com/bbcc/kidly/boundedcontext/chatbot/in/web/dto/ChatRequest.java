package com.bbcc.kidly.boundedcontext.chatbot.in.web.dto;

import com.bbcc.kidly.boundedcontext.chatbot.app.port.ChatbotAi.Speaker;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.ChatbotAi.Turn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 회원 질문과 이전 대화(선택, 오래된 것부터 최대 10개).
 */
public record ChatRequest(
    @NotBlank @Size(max = 500) String message,
    @Size(max = 10) List<@NotNull @Valid HistoryTurn> history) {

    public List<Turn> historyTurns() {
        if (history == null) {
            return List.of();
        }
        return history.stream().map(turn -> new Turn(turn.role(), turn.content())).toList();
    }

    public record HistoryTurn(
        @NotNull Speaker role,
        @NotBlank @Size(max = 1000) String content) {
    }
}
