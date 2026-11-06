package com.bbcc.kidly.boundedcontext.review.in.mcp;

import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService.ModerationOverview;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * moderation_queue: 리뷰 검사 현황(상태별 수 · 가장 오래된 대기 · 최근 숨김 ID · 이유 · 시각). 본문 · 작성자는 담지 않는다.
 */
class ModerationQueueTool implements CommunityMcpTool {

    private final ReviewModerationAdminService moderation;
    private final int maxResults;

    ModerationQueueTool(ReviewModerationAdminService moderation, int maxResults) {
        this.moderation = moderation;
        this.maxResults = maxResults;
    }

    @Override
    public String name() {
        return "moderation_queue";
    }

    @Override
    public String description() {
        return "리뷰 검사 현황: 상태별 리뷰 수(PENDING · 재시도 상한 도달 PENDING_EXHAUSTED · PASSED · HIDDEN · "
            + "CONFIRMED_HIDDEN · RESTORED), 가장 오래된 대기 리뷰의 작성 시각, 최근 숨긴 리뷰 ID · 이유 분류 · 시각.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Map<String, Object> call(McpToolArguments arguments) {
        ModerationOverview overview = moderation.overview(maxResults);
        List<Map<String, Object>> recent = overview.recentHidden().stream().map(hidden -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reviewId", hidden.reviewId());
            item.put("reason", hidden.reason());
            item.put("moderatedAt", McpValues.time(hidden.moderatedAt()));
            return item;
        }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", overview.counts());
        out.put("oldestPendingCreatedAt", McpValues.time(overview.oldestPendingCreatedAt()));
        out.put("recentHidden", recent);
        return out;
    }
}
