package com.bbcc.kidly.boundedcontext.order.in.web;

import com.bbcc.kidly.boundedcontext.order.app.order.PaymentRecoveryService;
import com.bbcc.kidly.boundedcontext.order.in.web.dto.PaymentRecoveryResponse;
import com.bbcc.kidly.global.response.PageResponse;
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
 * 관리자 결제 반영 실패 API(#353). /api/v1/admin/** 라 ADMIN만 호출할 수 있다(공용 보안 설정).
 * 예치금은 빠졌는데 결제완료 반영이 실패한 주문을 보고, 재처리하거나 취소(환불)로 끝낸다.
 */
@RestController
@RequestMapping(AdminPaymentRecoveryController.RECOVERIES_PATH)
@RequiredArgsConstructor
public class AdminPaymentRecoveryController {

    public static final String RECOVERIES_PATH = AdminOrderController.ADMIN_ORDERS_PATH + "/recoveries";

    private final PaymentRecoveryService recoveryService;
    private final PaymentRecoveryRetry recoveryRetry;

    /**
     * 최근 기록부터. status(PENDING · RECOVERED · CANCELLED · NOT_RETRYABLE)를 주면 그 상태만. size는 1~100.
     */
    @GetMapping
    public PageResponse<PaymentRecoveryResponse> getRecoveries(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(recoveryService.getRecoveries(status, page, size).map(PaymentRecoveryResponse::from));
    }

    /**
     * 보관한 결제 완료로 다시 반영한다. 복구되면 RECOVERED, 다시 실패하면 PENDING(시도 횟수 · 이유 갱신), 둘 다 200.
     * 이미 복구됐으면 그대로 200.
     */
    @PostMapping("/{recoveryId}/retry")
    public PaymentRecoveryResponse retry(@PathVariable Long recoveryId) {
        return PaymentRecoveryResponse.from(recoveryRetry.retry(recoveryId));
    }

    /**
     * 주문을 취소해(결제 환불 · 재고 복구) 기록을 CANCELLED로 끝낸다. 이미 취소로 종료됐으면 그대로 200.
     */
    @PostMapping("/{recoveryId}/cancel")
    public PaymentRecoveryResponse cancel(@PathVariable Long recoveryId) {
        return PaymentRecoveryResponse.from(recoveryService.cancel(recoveryId));
    }
}
