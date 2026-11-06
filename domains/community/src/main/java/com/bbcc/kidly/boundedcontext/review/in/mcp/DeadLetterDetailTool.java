package com.bbcc.kidly.boundedcontext.review.in.mcp;

import com.bbcc.kidly.boundedcontext.review.app.CommunityDeadLetterService;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.Summary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * dead_letter_detail: DLT 한 건(위치 · 원래 이벤트 식별 · 실패 종류 · 재처리 결과). 원본 본문 · 예외 메시지 · 스택 · 처리한 관리자는
 * 담지 않고, 자유 문구가 될 수 있는 칸은 정해진 형식일 때만 내보낸다.
 */
class DeadLetterDetailTool implements CommunityMcpTool {

    private final CommunityDeadLetterService deadLetters;

    DeadLetterDetailTool(CommunityDeadLetterService deadLetters) {
        this.deadLetters = deadLetters;
    }

    @Override
    public String name() {
        return "dead_letter_detail";
    }

    @Override
    public String description() {
        return "DLT 보관 한 건의 위치 · 원래 이벤트 식별 · 실패 종류 · 재처리 결과. 원본 본문 · 예외 메시지는 담지 않는다.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "properties", Map.of("id", Map.of("type", List.of("integer", "string"), "description", "DLT 보관 ID")),
            "required", List.of("id"));
    }

    @Override
    public Map<String, Object> call(McpToolArguments arguments) {
        Summary summary = deadLetters.get(arguments.requiredPositiveLong("id"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", summary.getId());
        out.put("dltTopic", McpSafeText.topic(summary.getDltTopic()));
        out.put("dltPartition", summary.getDltPartition());
        out.put("dltOffset", summary.getDltOffset());
        out.put("sourceTopic", McpSafeText.topic(summary.getSourceTopic()));
        out.put("sourcePartition", summary.getSourcePartition());
        out.put("sourceOffset", summary.getSourceOffset());
        out.put("messageKey", McpSafeText.identifier(summary.getMessageKey()));
        out.put("eventType", McpSafeText.eventType(summary.getEventType()));
        out.put("eventId", McpSafeText.identifier(summary.getEventId()));
        out.put("failureType", McpSafeText.exceptionName(summary.getFailureType()));
        out.put("state", McpValues.name(summary.getState()));
        out.put("replayCount", summary.getReplayCount());
        out.put("lastReplayedAt", McpValues.time(summary.getLastReplayedAt()));
        out.put("lastReplayFailureType", McpSafeText.exceptionName(summary.getLastReplayFailureType()));
        out.put("createdAt", McpValues.time(summary.getCreatedAt()));
        return out;
    }
}
