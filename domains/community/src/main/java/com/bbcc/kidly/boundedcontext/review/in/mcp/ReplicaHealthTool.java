package com.bbcc.kidly.boundedcontext.review.in.mcp;

import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService;
import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService.ReplicaHealth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * replica_health: 상품 복사본 수 · 표시 상태별 수 · 마지막 반영 시각(빠진 상품 · 반영 지연 진단).
 */
class ReplicaHealthTool implements CommunityMcpTool {

    private final ReplicaHealthService replicaHealth;

    ReplicaHealthTool(ReplicaHealthService replicaHealth) {
        this.replicaHealth = replicaHealth;
    }

    @Override
    public String name() {
        return "replica_health";
    }

    @Override
    public String description() {
        return "커뮤니티가 가진 상품 복사본 수 · 표시 상태별 수 · 마지막 반영 시각 · 가장 늦은 원본 이벤트 시각.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Map<String, Object> call(McpToolArguments arguments) {
        ReplicaHealth health = replicaHealth.health();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", health.total());
        out.put("byDisplayStatus", health.byDisplayStatus());
        out.put("lastUpdatedAt", McpValues.time(health.lastUpdatedAt()));
        out.put("lastSourceOccurredAt", McpValues.time(health.lastSourceOccurredAt()));
        return out;
    }
}
