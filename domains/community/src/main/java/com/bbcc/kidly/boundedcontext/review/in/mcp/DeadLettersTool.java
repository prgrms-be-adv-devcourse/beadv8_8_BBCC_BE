package com.bbcc.kidly.boundedcontext.review.in.mcp;

import com.bbcc.kidly.boundedcontext.review.app.CommunityDeadLetterService;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.State;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.Summary;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/**
 * dead_letters: 커뮤니티 DLT 보관 목록(최신순). 항목은 ID · 원래 토픽 · eventType · 상태 · 재처리 횟수 · 실패 예외 종류 · 시각만.
 */
class DeadLettersTool implements CommunityMcpTool {

    private static final int DEFAULT_SIZE = 20;

    private final CommunityDeadLetterService deadLetters;
    private final int maxResults;

    DeadLettersTool(CommunityDeadLetterService deadLetters, int maxResults) {
        this.deadLetters = deadLetters;
        this.maxResults = maxResults;
    }

    @Override
    public String name() {
        return "dead_letters";
    }

    @Override
    public String description() {
        return "커뮤니티가 받지 못한 이벤트(DLT 보관) 목록. 상태별 · 쪽 단위. 원본 본문 · 예외 메시지는 담지 않는다.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
            "state", Map.of("type", "string", "enum", Arrays.stream(State.values()).map(State::name).toList(),
                "description", "기본 OPEN"),
            "page", Map.of("type", "integer", "minimum", 0, "description", "0부터, 기본 0"),
            "size", Map.of("type", "integer", "minimum", 1, "maximum", maxResults,
                "description", "기본 " + Math.min(DEFAULT_SIZE, maxResults) + ", 상한보다 크면 상한으로 줄인다")));
    }

    @Override
    public Map<String, Object> call(McpToolArguments arguments) {
        String state = arguments.optionalText("state").orElse(State.OPEN.name());
        int page = arguments.optionalNonNegativeInt("page", 0);
        int size = Math.min(arguments.optionalPositiveInt("size", Math.min(DEFAULT_SIZE, maxResults)), maxResults);
        Page<Summary> result = deadLetters.list(state, page, size);
        List<Map<String, Object>> items = result.getContent().stream().map(DeadLettersTool::item).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", state);
        out.put("page", page);
        out.put("size", size);
        out.put("totalElements", result.getTotalElements());
        out.put("items", items);
        return out;
    }

    private static Map<String, Object> item(Summary summary) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", summary.getId());
        item.put("sourceTopic", McpSafeText.topic(summary.getSourceTopic()));
        item.put("eventType", McpSafeText.eventType(summary.getEventType()));
        item.put("state", McpValues.name(summary.getState()));
        item.put("replayCount", summary.getReplayCount());
        item.put("failureType", McpSafeText.exceptionName(summary.getFailureType()));
        item.put("createdAt", McpValues.time(summary.getCreatedAt()));
        return item;
    }
}
