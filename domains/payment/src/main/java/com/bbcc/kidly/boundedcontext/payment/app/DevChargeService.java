package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.DevChargeResult;
import com.bbcc.kidly.boundedcontext.payment.domain.DevCharge;
import com.bbcc.kidly.boundedcontext.payment.domain.DevChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토스 결제창 없이 회원 지갑에 개발용 예치금을 넣는다(#1010). dev에서 시드 회원으로 결제 · 환불을 확인하려고 쓴다.
 * local · test · dev 프로필에서만 등록된다. prod에 열리면 누구나 예치금을 만들 수 있다.
 * 토스 충전이 아니라서 충전(charge) · 충전 지표에 넣지 않고, 기록(dev_charge)과 원장 DEV_CHARGE만 남긴다.
 */
@Profile({"local", "test", "dev"})
@Service
@RequiredArgsConstructor
public class DevChargeService {

    private final WalletRepository walletRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final DevChargeRepository devChargeRepository;

    /**
     * 지갑이 없으면 만들고 락으로 읽어 입금한다. 탈퇴로 동결된 지갑은 충전처럼 409다.
     */
    @Transactional
    public DevChargeResult charge(Long memberId, long amount) {
        walletRepository.createIfAbsent(memberId, LocalDateTime.now());
        Wallet wallet = walletRepository.findWithLockByMemberId(memberId).orElseThrow();
        if (wallet.isFrozen()) {
            throw new BusinessException(PaymentErrorCode.WALLET_FROZEN);
        }
        DevCharge devCharge = devChargeRepository.save(DevCharge.of(wallet, amount));
        wallet.deposit(amount);
        WalletLedger ledger = walletLedgerRepository.save(WalletLedger.record(
            wallet.getId(), WalletLedgerType.DEV_CHARGE, amount, wallet.getBalance(), devCharge.getId()));
        PaymentActionLog.record(PaymentAction.DEV_CHARGED,
            "memberId={} walletId={} devChargeId={} amount={} balance={} ledgerId={}",
            memberId, wallet.getId(), devCharge.getId(), amount, wallet.getBalance(), ledger.getId());
        return new DevChargeResult(devCharge.getId(), ledger.getId(), amount, wallet.getBalance());
    }
}
