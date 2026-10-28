package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeConfirmResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeConfirmTarget;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ConfirmChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 충전 승인 흐름. 승인 중 커밋 → 트랜잭션 밖에서 토스 승인 → 결과 반영 순서로, 외부 호출 동안 DB 트랜잭션을 잡지 않는다.
 * 결과를 모르면(시간 초과 · 5xx) 실패로 처리하지 않고 CONFIRMING으로 둔다. 돈이 빠져나갔을 수 있어서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeConfirmService {

    private final ChargeService chargeService;
    private final PaymentGateway paymentGateway;

    public ChargeConfirmResult confirm(Long memberId, ConfirmChargeRequest request) {
        ChargeConfirmTarget target = chargeService.startConfirming(memberId, request);
        PgResult<PgApproval> result = paymentGateway.confirm(
            request.paymentKey(), target.orderId(), target.amount(), target.orderId());
        return switch (result) {
            case PgResult.Success<PgApproval> success -> chargeService.approve(target.chargeId(), success.value());
            case PgResult.Rejected<PgApproval> rejected -> {
                chargeService.fail(target.chargeId(), rejected.code());
                throw rejectedException(rejected);
            }
            case PgResult.Unknown<PgApproval> unknown -> {
                log.warn("충전 승인 결과를 알 수 없어 승인 중으로 둡니다. chargeId={}, reason={}",
                    target.chargeId(), unknown.reason());
                yield ChargeConfirmResult.confirmingOf(target);
            }
        };
    }

    private static BusinessException rejectedException(PgResult.Rejected<PgApproval> rejected) {
        String message = rejected.message();
        return message == null || message.isBlank()
            ? new BusinessException(PaymentErrorCode.CHARGE_REJECTED)
            : new BusinessException(PaymentErrorCode.CHARGE_REJECTED, message);
    }
}
