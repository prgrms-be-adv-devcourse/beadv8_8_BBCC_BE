package com.bbcc.kidly.boundedcontext.review.in.mcp;

import com.bbcc.kidly.boundedcontext.review.app.CommunityDeadLetterService;
import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 도구 정의 목록과 호출 분기(MCP SDK를 모른다). 인자 오류 · 업무 오류는 도구 오류(정해진 문구)로, 예상 못 한 예외는 예외 종류만
 * 로그에 남기고 일반 문구로 돌려준다(예외 메시지 · 값을 내보내지 않는다).
 */
@Slf4j
public class CommunityMcpTools {

    private final Map<String, CommunityMcpTool> byName = new LinkedHashMap<>();

    public CommunityMcpTools(List<CommunityMcpTool> tools) {
        tools.forEach(tool -> byName.put(tool.name(), tool));
    }

    /**
     * 커뮤니티 운영 조회 도구 다섯 개.
     *
     * @param maxResults 목록 결과 상한(이보다 큰 size는 상한으로 줄인다)
     */
    public static CommunityMcpTools of(ReviewQueryService reviewQueries, CommunityDeadLetterService deadLetters,
        ReviewModerationAdminService moderation, ReplicaHealthService replicaHealth, int maxResults) {
        return new CommunityMcpTools(List.of(
            new ReviewSummaryTool(reviewQueries),
            new DeadLettersTool(deadLetters, maxResults),
            new DeadLetterDetailTool(deadLetters),
            new ModerationQueueTool(moderation, maxResults),
            new ReplicaHealthTool(replicaHealth)));
    }

    public List<CommunityMcpTool> tools() {
        return List.copyOf(byName.values());
    }

    public ToolOutcome call(String name, Map<String, Object> arguments) {
        CommunityMcpTool tool = byName.get(name);
        if (tool == null) {
            return ToolOutcome.failure("모르는 도구입니다. tools/list의 이름으로 불러 주세요");
        }
        try {
            return ToolOutcome.ok(tool.call(new McpToolArguments(arguments)));
        } catch (McpToolArgumentException e) {
            return ToolOutcome.failure(e.getMessage());
        } catch (BusinessException e) {
            return ToolOutcome.failure(e.getErrorCode().title() + ": " + e.getDetail());
        } catch (RuntimeException e) {
            log.warn("MCP 도구 조회가 예외로 끝났습니다. tool={} error={}", name, e.getClass().getSimpleName());
            return ToolOutcome.failure("조회하지 못했습니다. 잠시 뒤 다시 시도해 주세요");
        }
    }
}
