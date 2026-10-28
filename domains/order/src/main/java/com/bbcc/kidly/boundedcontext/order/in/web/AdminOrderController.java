package com.bbcc.kidly.boundedcontext.order.in.web;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.in.web.dto.AdminOrderSummaryResponse;
import com.bbcc.kidly.boundedcontext.order.in.web.dto.OrderStatusHistoryResponse;
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

    private final OrderService orderService;

    /**
     * 모든 회원의 주문을 최근 주문부터. size는 1~100.
     */
    @GetMapping
    public PageResponse<AdminOrderSummaryResponse> getAllOrders(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(orderService.getAllOrders(page, size).map(AdminOrderSummaryResponse::from));
    }

    /**
     * 주문 · 주문상품 · 배송 · 결제 복구 기록의 상태 변경 이력(#442). 오래된 것부터, 없으면 []. 없는 주문이면 404.
     */
    @GetMapping("/{orderId}/history")
    public List<OrderStatusHistoryResponse> getHistory(@PathVariable Long orderId) {
        return orderService.getHistory(orderId).stream().map(OrderStatusHistoryResponse::from).toList();
    }
}
