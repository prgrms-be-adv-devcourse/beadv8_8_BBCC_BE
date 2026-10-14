package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeCancelResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeCancelTarget;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentKeyCipher;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgCancellation;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 충전 취소 흐름. 취소 중 커밋(충전액 차감) → 트랜잭션 밖에서 토스 취소 → 결과 반영 순서다.
 * 결과를 모르면(시간 초과 · 5xx) CANCELLING으로 두고, 충전 정리 스케줄러가 같은 멱등 키로 다시 취소한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeCancelService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "charge-cancellation-";

    private final ChargeService chargeService;
    private final PaymentGateway paymentGateway;
    private final PaymentKeyCipher paymentKeyCipher;

    public ChargeCancelResult cancel(Long memberId, Long chargeId) {
        ChargeCancelTarget target = chargeService.startCancel(memberId, chargeId);
        return switch (requestCancel(target)) {
            case PgResult.Success<PgCancellation> success ->
                chargeService.completeCancel(target.chargeId(), success.value());
            case PgResult.Rejected<PgCancellation> rejected -> {
                chargeService.failCancel(target.chargeId(), rejected.code());
                throw rejectedException(rejected);
            }
            case PgResult.Unknown<PgCancellation> unknown -> {
                log.warn("충전 취소 결과를 알 수 없어 취소 중으로 둡니다. chargeId={}, reason={}",
                    target.chargeId(), unknown.reason());
                yield ChargeCancelResult.cancellingOf(target);
            }
        };
    }

    /**
     * 취소 중으로 남은 충전을 같은 멱등 키로 다시 취소해 확정한다. 아직 모르면 다음 실행으로 미룬다.
     */
    public void resume(ChargeCancelTarget target) {
        switch (requestCancel(target)) {
            case PgResult.Success<PgCancellation> success -> {
                chargeService.completeCancel(target.chargeId(), success.value());
                log.info("취소 중 충전을 다시 취소해 완료했습니다. chargeId={}", target.chargeId());
            }
            case PgResult.Rejected<PgCancellation> rejected -> {
                chargeService.failCancel(target.chargeId(), rejected.code());
                log.info("취소 중 충전의 취소가 거절돼 되돌렸습니다. chargeId={}, code={}",
                    target.chargeId(), rejected.code());
            }
            case PgResult.Unknown<PgCancellation> unknown ->
                log.info("충전 취소 결과를 아직 알 수 없어 다음에 다시 봅니다. chargeId={}, reason={}",
                    target.chargeId(), unknown.reason());
        }
    }

    private PgResult<PgCancellation> requestCancel(ChargeCancelTarget target) {
        return paymentGateway.cancel(paymentKeyCipher.decrypt(target.paymentKeyEnc()), target.reason(),
            IDEMPOTENCY_KEY_PREFIX + target.cancellationId());
    }

    private static BusinessException rejectedException(PgResult.Rejected<PgCancellation> rejected) {
        String message = rejected.message();
        return message == null || message.isBlank()
            ? new BusinessException(PaymentErrorCode.CHARGE_CANCEL_REJECTED)
            : new BusinessException(PaymentErrorCode.CHARGE_CANCEL_REJECTED, message);
    }
}
