package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.StuckCharge;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 충전 정리 스케줄러가 끝내지 못하고 승인 중으로 남긴 충전을 관리자가 찾아 끝낸다.
 * 토스 승인 금액이 충전 금액과 다르거나 토스에서 부분 취소된 충전이 여기 남는다(ChargeReconcileService.reconcile).
 * 운영자는 토스 상점관리자에서 돈을 먼저 정리한 뒤 실패로 끝낸다. 입금하는 길은 두지 않는다.
 * 고객에게 입금하려면 토스 승인을 충전 금액대로 두면 스케줄러가 입금한다.
 */
@Service
@RequiredArgsConstructor
public class StuckChargeService {

    private final ChargeRepository chargeRepository;
    private final WalletRepository walletRepository;
    private final ChargeService chargeService;
    private final PaymentGateway paymentGateway;
    private final ChargeProperties chargeProperties;

    /**
     * 스케줄러와 같은 기준으로 오래 승인 중인 충전을 오래된 순으로 준다.
     */
    @Transactional(readOnly = true)
    public Page<StuckCharge> getStuck(int page, int size) {
        Page<Charge> charges = chargeRepository.findAllByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscIdAsc(
            ChargeStatus.CONFIRMING, staleBefore(), PageRequest.of(page, size));
        Map<Long, Long> memberIds = walletRepository.findAllByIdIn(charges.map(Charge::getWalletId).toSet())
            .stream()
            .collect(Collectors.toMap(Wallet::getId, Wallet::getMemberId));
        return charges.map(charge -> StuckCharge.of(charge, memberIds.get(charge.getWalletId())));
    }

    /**
     * 멈춘 충전을 실패로 끝낸다. 토스 조회는 트랜잭션 밖에서 하고, 반영할 때 충전 락을 잡고 다시 확인한다.
     * 토스가 충전 금액대로 승인했으면 스케줄러가 입금할 충전이라 거절하고, 토스 결과를 모르면 돈이 들어올 수 있어 거절한다.
     *
     * @param adminId 처리한 관리자
     */
    public StuckCharge close(Long chargeId, Long adminId) {
        LocalDateTime staleBefore = staleBefore();
        Charge charge = chargeRepository.findById(chargeId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.CHARGE_NOT_FOUND));
        if (!charge.isStuckConfirming(staleBefore)) {
            throw new BusinessException(PaymentErrorCode.CHARGE_NOT_STUCK);
        }
        String pgResult = switch (paymentGateway.findByOrderId(charge.getTossOrderId())) {
            case PgResult.Success<PgApproval> success when success.value().amount() == charge.getAmount() ->
                throw new BusinessException(PaymentErrorCode.CHARGE_APPROVED_AT_PG);
            case PgResult.Success<PgApproval> success -> "APPROVED_AMOUNT=" + success.value().amount();
            case PgResult.Rejected<PgApproval> rejected -> rejected.code();
            case PgResult.Unknown<PgApproval> unknown ->
                throw new BusinessException(PaymentErrorCode.PG_RESULT_UNKNOWN);
        };
        return chargeService.closeStuck(chargeId, staleBefore, pgResult, adminId);
    }

    private LocalDateTime staleBefore() {
        return LocalDateTime.now().minus(chargeProperties.confirmingStaleAfter());
    }
}
