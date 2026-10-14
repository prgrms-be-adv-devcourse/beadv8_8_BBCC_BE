package com.bbcc.kidly.boundedcontext.payment;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgCancellation;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 통합 · 기능 테스트에서 토스 대신 쓴다. paymentKey로 결과를 고른다.
 * 실제 토스 연동은 TossPaymentGatewayTest가 확인한다.
 */
@Primary
@Component
public class FakePaymentGateway implements PaymentGateway {

    public static final String REJECTED_KEY = "rejected-key";
    public static final String UNKNOWN_KEY = "unknown-key";
    public static final String REJECTED_CODE = "REJECT_CARD_COMPANY";
    public static final String REJECTED_MESSAGE = "카드사에서 거절했습니다";

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
        return new PgResult.Unknown<>("테스트에서 쓰지 않는다");
    }
}
