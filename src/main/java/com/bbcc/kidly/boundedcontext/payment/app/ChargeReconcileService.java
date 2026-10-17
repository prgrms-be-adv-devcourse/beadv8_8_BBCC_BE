package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeCancelTarget;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeReconcileTarget;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgCancellation;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 끝나지 않은 충전을 정리한다. 승인 중으로 오래 남은 충전은 토스 조회로 확정하고, 만료된 대기 충전은 만료로 바꾼다.
 * 취소 중으로 오래 남은 충전은 여기서 찾고, 다시 취소하는 일은 ChargeCancelService가 한다.
 * 토스에서 직접 취소된 충전은 토스 웹훅을 받아 맞춘다.
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

    /**
     * 토스에서 직접 취소됐다는 웹훅을 받은 충전을 맞춘다. 웹훅 본문은 믿지 않고 토스를 다시 조회해 전액 취소일 때만 반영한다.
     * 우리 충전이 아니거나 완료가 아닌 충전은 넘어간다. 조회 결과를 모르면 PG_RESULT_UNKNOWN을 던져 토스가 웹훅을 다시 보내게 한다.
     */
    public void reconcileExternalCancel(String tossOrderId) {
        Optional<Charge> found = chargeRepository.findByTossOrderId(tossOrderId)
            .filter(charge -> charge.getStatus() == ChargeStatus.DONE);
        if (found.isEmpty()) {
            log.info("완료된 충전이 아니어서 토스 취소 웹훅을 넘어갑니다. tossOrderId={}", tossOrderId);
            return;
        }
        Long chargeId = found.get().getId();
        switch (paymentGateway.findCancellationByOrderId(tossOrderId)) {
            case PgResult.Success<PgCancellation> success -> {
                chargeService.applyExternalCancel(chargeId, success.value());
                log.info("토스에서 직접 취소된 충전을 반영했습니다. chargeId={}", chargeId);
            }
            case PgResult.Rejected<PgCancellation> rejected
                when PaymentGateway.PARTIAL_CANCELED.equals(rejected.code()) ->
                log.error("토스에서 부분 취소된 충전은 아직 반영하지 않습니다. 운영자가 확인해야 합니다. chargeId={}", chargeId);
            case PgResult.Rejected<PgCancellation> rejected ->
                log.info("토스에서 전액 취소되지 않은 충전이라 그대로 둡니다. chargeId={}, code={}", chargeId, rejected.code());
            case PgResult.Unknown<PgCancellation> unknown -> {
                log.warn("토스 취소 조회 결과를 알 수 없습니다. chargeId={}, reason={}", chargeId, unknown.reason());
                throw new BusinessException(PaymentErrorCode.PG_RESULT_UNKNOWN);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ChargeCancelTarget> findStaleCancelling() {
        LocalDateTime before = LocalDateTime.now().minus(chargeProperties.cancellingStaleAfter());
        return chargeRepository.findAllByStatusAndUpdatedAtBefore(ChargeStatus.CANCELLING, before).stream()
            .map(ChargeCancelTarget::from)
            .toList();
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
            .ifPresent(charge -> {
                charge.expire();
                PaymentActionLog.record(PaymentAction.CHARGE_EXPIRED, "walletId={} chargeId={}",
                    charge.getWalletId(), charge.getId());
            });
    }
}
