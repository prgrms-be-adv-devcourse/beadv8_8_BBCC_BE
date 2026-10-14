package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeReconcileTarget;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 끝나지 않은 충전을 정리한다. 승인 중으로 오래 남은 충전은 토스 조회로 확정하고, 만료된 대기 충전은 만료로 바꾼다.
 * 토스 조회는 트랜잭션 밖에서 하고, 반영은 ChargeService의 승인 · 실패를 그대로 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeReconcileService {

    private final ChargeRepository chargeRepository;
    private final ChargeService chargeService;
    private final PaymentGateway paymentGateway;
    private final ChargeProperties chargeProperties;

    @Transactional(readOnly = true)
    public List<ChargeReconcileTarget> findStaleConfirming() {
        LocalDateTime before = LocalDateTime.now().minus(chargeProperties.confirmingStaleAfter());
        return chargeRepository.findAllByStatusAndUpdatedAtBefore(ChargeStatus.CONFIRMING, before).stream()
            .map(ChargeReconcileTarget::from)
            .toList();
    }

    /**
     * 토스가 승인했으면 입금하고, 승인되지 않고 끝났으면 실패로 바꾼다. 아직 모르면 다음 실행으로 미룬다.
     */
    public void reconcile(ChargeReconcileTarget target) {
        PgResult<PgApproval> result = paymentGateway.findByOrderId(target.orderId());
        switch (result) {
            case PgResult.Success<PgApproval> success -> {
                if (success.value().amount() != target.amount()) {
                    log.error("토스 승인 금액이 충전 금액과 달라 반영하지 않습니다. chargeId={}, chargeAmount={}, approvedAmount={}",
                        target.chargeId(), target.amount(), success.value().amount());
                    return;
                }
                chargeService.approve(target.chargeId(), success.value());
                log.info("승인 중 충전을 토스 조회로 승인했습니다. chargeId={}", target.chargeId());
            }
            case PgResult.Rejected<PgApproval> rejected -> {
                chargeService.fail(target.chargeId(), rejected.code());
                log.info("승인 중 충전을 토스 조회로 실패 처리했습니다. chargeId={}, code={}", target.chargeId(), rejected.code());
            }
            case PgResult.Unknown<PgApproval> unknown ->
                log.info("충전 승인 결과를 아직 알 수 없어 다음에 다시 봅니다. chargeId={}, reason={}",
                    target.chargeId(), unknown.reason());
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findOverduePendingIds() {
        return chargeRepository.findAllByStatusAndExpiresAtBefore(ChargeStatus.PENDING, LocalDateTime.now()).stream()
            .map(Charge::getId)
            .toList();
    }

    /**
     * 그사이 승인이 시작됐거나 아직 만료 전이면 그대로 둔다.
     */
    @Transactional
    public void expire(Long chargeId) {
        LocalDateTime now = LocalDateTime.now();
        chargeRepository.findWithLockById(chargeId)
            .filter(charge -> charge.getStatus() == ChargeStatus.PENDING && charge.getExpiresAt().isBefore(now))
            .ifPresent(Charge::expire);
    }
}
