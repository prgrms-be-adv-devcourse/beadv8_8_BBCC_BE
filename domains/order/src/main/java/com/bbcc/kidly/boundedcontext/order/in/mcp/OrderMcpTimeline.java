package com.bbcc.kidly.boundedcontext.order.in.mcp;

import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OrderStatusHistoryResponse;
import java.util.List;

/**
 * 주문 상태 이력(관리자 이력 API와 같은 칸). 이력이 max-results보다 길면 최근 것만 담고 truncated true.
 *
 * @param totalCount 전체 이력 줄 수
 */
public record OrderMcpTimeline(String orderId, int totalCount, boolean truncated,
    List<OrderStatusHistoryResponse> history) {
}
