package com.bbcc.kidly.boundedcontext.payment;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgCancellation;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 통합 · 기능 테스트에서 토스 대신 쓴다. 승인은 paymentKey로, 조회는 테스트가 정해 둔 주문번호별 결과로 고른다.
 * 실제 토스 연동은 TossPaymentGatewayTest가 확인한다.
 */
@Primary
@Component
public class FakePaymentGateway implements PaymentGateway {

    public static final String REJECTED_KEY = "rejected-key";
    public static final String UNKNOWN_KEY = "unknown-key";
    public static final String REJECTED_CODE = "REJECT_CARD_COMPANY";
    public static final String REJECTED_MESSAGE = "카드사에서 거절했습니다";

    // 주문번호별 조회 결과. 정해 두지 않은 주문번호는 결과를 모름. 테스트가 끝나면 reset()으로 비운다
    private final Map<String, PgResult<PgApproval>> lookups = new ConcurrentHashMap<>();

    public void willFindApproved(String orderId, long amount) {
        PgApproval approval = new PgApproval("payment-key-" + orderId, amount, LocalDateTime.now());
        lookups.put(orderId, new PgResult.Success<>(approval));
    }

    public void willFindRejected(String orderId, String code) {
        lookups.put(orderId, new PgResult.Rejected<>(code, "승인되지 않고 끝난 결제입니다"));
    }

    public void reset() {
        lookups.clear();
    }

    @Override
    public PgResult<PgApproval> confirm(String paymentKey, String orderId, long amount, String idempotencyKey) {
        return switch (paymentKey) {
            case REJECTED_KEY -> new PgResult.Rejected<>(REJECTED_CODE, REJECTED_MESSAGE);
            case UNKNOWN_KEY -> new PgResult.Unknown<>("timeout");
            default -> new PgResult.Success<>(new PgApproval(paymentKey, amount, LocalDateTime.now()));
        };
    }

    @Override
    public PgResult<PgCancellation> cancel(String paymentKey, String reason, String idempotencyKey) {
        return new PgResult.Unknown<>("테스트에서 쓰지 않는다");
    }

    @Override
    public PgResult<PgApproval> findByOrderId(String orderId) {
        return lookups.getOrDefault(orderId, new PgResult.Unknown<>("정해 둔 조회 결과 없음"));
    }
}
