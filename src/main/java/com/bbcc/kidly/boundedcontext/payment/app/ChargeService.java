package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeCancelResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeCancelTarget;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeConfirmResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeConfirmTarget;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeDetail;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeRequestResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ConfirmChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentKeyCipher;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgCancellation;
import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeCancellation;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 충전의 상태를 바꾸는 트랜잭션 단위. 토스 호출은 트랜잭션 밖(ChargeConfirmService · ChargeCancelService)에서 한다.
 * 남의 지갑 충전은 없는 충전처럼 404다.
 */
@Service
@RequiredArgsConstructor
public class ChargeService {

    private static final Duration EXPIRES_IN = Duration.ofMinutes(30);
    private static final String CANCEL_REASON = "예치금 충전 취소";

    private final WalletRepository walletRepository;
    private final ChargeRepository chargeRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final PaymentKeyCipher paymentKeyCipher;

    @Transactional
    public ChargeRequestResult request(Long memberId, ChargeRequest request) {
        Wallet wallet = walletOf(memberId);
        Charge charge = chargeRepository.save(Charge.request(wallet.getId(), UUID.randomUUID().toString(),
            request.amount(), LocalDateTime.now().plus(EXPIRES_IN)));
        return ChargeRequestResult.from(charge);
    }

    @Transactional(readOnly = true)
    public Page<ChargeDetail> getCharges(Long memberId, int page, int size) {
        Wallet wallet = walletOf(memberId);
        return chargeRepository.findAllByWalletIdOrderByIdDesc(wallet.getId(), PageRequest.of(page, size))
            .map(ChargeDetail::from);
    }

    @Transactional(readOnly = true)
    public ChargeDetail get(Long memberId, Long chargeId) {
        Wallet wallet = walletOf(memberId);
        return chargeRepository.findById(chargeId)
            .filter(charge -> charge.getWalletId().equals(wallet.getId()))
            .map(ChargeDetail::from)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.CHARGE_NOT_FOUND));
    }

    /**
     * 결제창에서 돌아온 금액을 대조하고 승인 중으로 커밋한다. 커밋해 두면 토스 호출 결과를 몰라도 충전이 남는다.
     */
    @Transactional
    public ChargeConfirmTarget startConfirming(Long memberId, ConfirmChargeRequest request) {
        Wallet wallet = walletOf(memberId);
        Charge charge = chargeRepository.findWithLockByTossOrderId(request.orderId())
            .filter(found -> found.getWalletId().equals(wallet.getId()))
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.CHARGE_NOT_FOUND));
        charge.startConfirming(request.amount(), LocalDateTime.now());
        return new ChargeConfirmTarget(charge.getId(), charge.getTossOrderId(), charge.getAmount());
    }

    /**
     * 토스 승인을 반영한다. 지갑을 락으로 읽어 입금하고, 원장에 CHARGE(ref_id = 충전 ID)를 남긴다.
     */
    @Transactional
    public ChargeConfirmResult approve(Long chargeId, PgApproval approval) {
        Charge charge = chargeOf(chargeId);
        Wallet wallet = walletRepository.findWithLockById(charge.getWalletId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));
        charge.approve(paymentKeyCipher.encrypt(approval.paymentKey()), approval.approvedAt());
        wallet.deposit(charge.getAmount());
        walletLedgerRepository.save(WalletLedger.record(
            wallet.getId(), WalletLedgerType.CHARGE, charge.getAmount(), wallet.getBalance(), charge.getId()));
        return ChargeConfirmResult.done(charge.getId(), charge.getAmount(), wallet.getBalance());
    }

    @Transactional
    public void fail(Long chargeId, String failureCode) {
        chargeOf(chargeId).fail(failureCode);
    }

    /**
     * 취소 중으로 커밋하면서 충전액을 먼저 뺀다. 토스 호출 동안 그 돈으로 결제하지 못하게 하려는 것이다.
     * 원장에는 CHARGE_CANCEL(ref_id = 취소 요청 ID)을 남긴다.
     */
    @Transactional
    public ChargeCancelTarget startCancel(Long memberId, Long chargeId) {
        Charge charge = chargeOf(chargeId);
        Wallet wallet = walletRepository.findWithLockById(charge.getWalletId())
            .filter(found -> found.getMemberId().equals(memberId))
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.CHARGE_NOT_FOUND));
        charge.requestCancel(CANCEL_REASON);
        if (!wallet.canWithdraw(charge.getAmount())) {
            throw new BusinessException(PaymentErrorCode.CHARGE_CANCEL_BALANCE_SHORT);
        }
        chargeRepository.saveAndFlush(charge);
        ChargeCancellation cancellation = charge.getLastCancellation();
        wallet.withdraw(cancellation.getAmount());
        walletLedgerRepository.save(WalletLedger.record(wallet.getId(), WalletLedgerType.CHARGE_CANCEL,
            cancellation.getAmount(), wallet.getBalance(), cancellation.getId()));
        return ChargeCancelTarget.from(charge);
    }

    @Transactional
    public ChargeCancelResult completeCancel(Long chargeId, PgCancellation cancellation) {
        Charge charge = chargeOf(chargeId);
        Wallet wallet = walletRepository.findWithLockById(charge.getWalletId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));
        LocalDateTime cancelledAt = cancellation.cancelledAt();
        charge.completeCancel(cancelledAt == null ? LocalDateTime.now() : cancelledAt);
        return ChargeCancelResult.cancelled(charge.getId(), charge.getAmount(), wallet.getBalance());
    }

    /**
     * 토스가 취소를 거절했으면 완료로 되돌리고, 먼저 뺀 충전액을 다시 넣어 원장에 CHARGE_CANCEL_REVERT를 남긴다.
     */
    @Transactional
    public void failCancel(Long chargeId, String failureCode) {
        Charge charge = chargeOf(chargeId);
        Wallet wallet = walletRepository.findWithLockById(charge.getWalletId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));
        charge.failCancel(failureCode);
        ChargeCancellation cancellation = charge.getLastCancellation();
        wallet.deposit(cancellation.getAmount());
        walletLedgerRepository.save(WalletLedger.record(wallet.getId(), WalletLedgerType.CHARGE_CANCEL_REVERT,
            cancellation.getAmount(), wallet.getBalance(), cancellation.getId()));
    }

    private Wallet walletOf(Long memberId) {
        return walletRepository.findByMemberId(memberId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));
    }

    // 상태를 바꾸는 승인 반영 · 실패 · 취소에서 쓴다. 락으로 읽어 겹친 요청은 앞 트랜잭션이 바꾼 상태를 본다
    private Charge chargeOf(Long chargeId) {
        return chargeRepository.findWithLockById(chargeId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.CHARGE_NOT_FOUND));
    }
}
