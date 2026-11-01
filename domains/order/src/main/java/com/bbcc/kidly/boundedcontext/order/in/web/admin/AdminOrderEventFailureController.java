package com.bbcc.kidly.boundedcontext.order.in.web.admin;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderEventFailureService;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.OrderEventFailureResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 이벤트 처리 실패 API(#449). /api/v1/admin/** 라 ADMIN만 호출할 수 있다(공용 보안 설정).
 * 재고 차감됨 · 재고 차감 실패 · 결제 실패 처리가 실패한 주문을 보고, 재처리하거나 기록을 종료한다.
 */
@RestController
@RequestMapping(AdminOrderEventFailureController.EVENT_FAILURES_PATH)
@RequiredArgsConstructor
public class AdminOrderEventFailureController {

    public static final String EVENT_FAILURES_PATH = AdminOrderController.ADMIN_ORDERS_PATH + "/event-failures";

    private final OrderEventFailureService failureService;
    private final OrderEventFailureRetry failureRetry;

    /**
     * 최근 기록부터. status(PENDING · RECOVERED · CANCELLED)를 주면 그 상태만. size는 1~100.
     */
    @GetMapping
    public PageResponse<OrderEventFailureResponse> getFailures(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(failureService.getFailures(status, page, size).map(OrderEventFailureResponse::from));
    }

    /**
     * 보관한 이벤트 데이터로 다시 처리한다. 복구되면 RECOVERED, 주문이 이미 끝났으면 CANCELLED(주문은 그대로),
     * 다시 실패하면 PENDING(시도 횟수 · 이유 갱신), 모두 200. 이미 복구됐으면 그대로 200.
     */
    @PostMapping("/{failureId}/retry")
    public OrderEventFailureResponse retry(@PathVariable Long failureId, @LoginMember AuthMember admin) {
        return OrderEventFailureResponse.from(failureRetry.retry(failureId, admin.id()));
    }

    /**
     * 기록만 CANCELLED로 종료한다(주문은 바꾸지 않음). 이미 종료됐으면 그대로 200.
     */
    @PostMapping("/{failureId}/cancel")
    public OrderEventFailureResponse cancel(@PathVariable Long failureId) {
        return OrderEventFailureResponse.from(failureService.cancel(failureId));
    }
}
