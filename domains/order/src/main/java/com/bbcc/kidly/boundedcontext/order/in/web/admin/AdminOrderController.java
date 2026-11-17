package com.bbcc.kidly.boundedcontext.order.in.web.admin;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderQueryService;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.AdminOrderSummaryResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OrderStatusHistoryResponse;
import com.bbcc.kidly.global.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 주문 API. /api/v1/admin/** 라 ADMIN만 호출할 수 있다(공용 보안 설정).
 */
@RestController
@RequestMapping(AdminOrderController.ADMIN_ORDERS_PATH)
@RequiredArgsConstructor
public class AdminOrderController {

    public static final String ADMIN_ORDERS_PATH = "/api/v1/admin/orders";

    private final OrderQueryService orderQueries;

    /**
     * 모든 회원의 주문을 최근 주문부터. size는 1~100. status(#1280, 선택)는 응답 status와 같은 값 하나(PENDING_PAYMENT · PAID ·
     * CANCELLED, 대문자 그대로)로 거르고 totalElements가 그 상태의 전체 건수가 된다. 모르는 값 · 빈 값은 400(INVALID_ORDER_STATUS).
     * 줄마다 cancelledAt(#1325, 취소 주문만). cancelledFrom(#1325, 선택, 한국 시간 yyyy-MM-ddTHH:mm:ss, 포함)이면 그 시각 이후 취소된
     * 주문만(status와 AND), 형식이 틀리면 400(INVALID_CANCELLED_FROM).
     */
    @GetMapping
    public PageResponse<AdminOrderSummaryResponse> getAllOrders(
        @RequestParam(required = false) String status,
        // 형식이 틀려도 기본 400(안내 없음) 대신 형식을 안내하는 INVALID_CANCELLED_FROM으로 답하게 서비스가 검사한다
        @RequestParam(required = false) String cancelledFrom,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(
            orderQueries.getAllOrders(status, cancelledFrom, page, size).map(AdminOrderSummaryResponse::from));
    }

    /**
     * 주문 · 주문상품 · 배송 · 결제 복구 기록의 상태 변경 이력(#442). 오래된 것부터, 없으면 []. 없는 주문이면 404.
     */
    @GetMapping("/{orderId}/history")
    public List<OrderStatusHistoryResponse> getHistory(@PathVariable Long orderId) {
        return orderQueries.getHistory(orderId).stream().map(OrderStatusHistoryResponse::from).toList();
    }
}
