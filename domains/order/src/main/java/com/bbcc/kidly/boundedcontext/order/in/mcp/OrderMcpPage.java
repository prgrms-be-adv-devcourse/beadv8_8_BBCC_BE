package com.bbcc.kidly.boundedcontext.order.in.mcp;

import java.util.List;

/**
 * MCP 목록 응답. size는 실제로 쓴 값(max-results로 줄였으면 줄인 값).
 */
public record OrderMcpPage<T>(List<T> items, int page, int size, long totalElements, int totalPages,
    boolean hasNext) {
}
