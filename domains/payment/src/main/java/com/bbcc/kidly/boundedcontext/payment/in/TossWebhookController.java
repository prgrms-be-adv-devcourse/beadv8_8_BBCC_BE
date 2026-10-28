package com.bbcc.kidly.boundedcontext.payment.in;

import com.bbcc.kidly.boundedcontext.payment.app.ChargeReconcileService;
import com.bbcc.kidly.boundedcontext.payment.in.dto.TossWebhookRequest;
import com.bbcc.kidly.global.security.PublicApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스 웹훅. 토스 상점관리자에서 직접 취소한 충전을 맞춘다.
 * 200이 아니면 토스가 다시 보내므로, 처리하지 않는 이벤트도 200이고 토스 조회 결과를 모를 때만 503이다.
 */
@RestController
@RequiredArgsConstructor
public class TossWebhookController {

    private final ChargeReconcileService chargeReconcileService;

    @PublicApi
    @PostMapping("/api/v1/webhooks/toss")
    public ResponseEntity<Void> receive(@RequestBody TossWebhookRequest request) {
        if (request.isPaymentCancelled()) {
            chargeReconcileService.reconcileExternalCancel(request.data().orderId());
        }
        return ResponseEntity.ok().build();
    }
}
