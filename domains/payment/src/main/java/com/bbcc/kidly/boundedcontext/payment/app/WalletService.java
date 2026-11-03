package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletBalanceResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletLedgerEntry;
import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletSummary;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.payment.app.dto.BalanceReturnResult;
import com.bbcc.kidly.boundedcontext.payment.domain.BalanceReturn;
import com.bbcc.kidly.boundedcontext.payment.domain.BalanceReturnRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 예치금 지갑 잔액과 입출금 내역(원장)을 조회한다. 지갑이 없으면 아직 쓰지 않은 지갑이라 잔액 0 · 빈 원장이다.
 * 회원이 탈퇴하면 지갑을 동결하고(#670), 관리자는 동결됐고 잔액이 남은 지갑을 조회해 환급한다.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final BalanceReturnRepository balanceReturnRepository;

    @Transactional(readOnly = true)
    public WalletBalanceResult getBalance(Long memberId) {
        return walletRepository.findByMemberId(memberId)
            .map(WalletBalanceResult::from)
            .orElse(new WalletBalanceResult(0L));
    }

    /**
     * 회원 탈퇴로 지갑을 동결한다. 지갑이 없으면 동결된 빈 지갑을 만든다(탈퇴 뒤 들어오는 환불 · 정산 입금을 놓치지 않게).
     * 같은 탈퇴를 다시 받아도 처음 동결한 시각을 그대로 둔다. 잔액은 건드리지 않는다.
     */
    @Transactional
    public void freeze(Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        walletRepository.createIfAbsent(memberId, now);
        Wallet wallet = walletRepository.findWithLockByMemberId(memberId).orElseThrow();
        if (wallet.freeze(now)) {
            PaymentActionLog.record(PaymentAction.WALLET_FROZEN, "memberId={} walletId={} balance={}",
                memberId, wallet.getId(), wallet.getBalance());
        }
    }

    /**
     * 탈퇴로 동결된 지갑의 남은 잔액을 운영자가 환급했다고 정리한다(#731). 실제 송금은 하지 않는다.
     * 지갑 락을 잡고, 관리자가 본 금액이 지금 잔액과 같을 때만 잔액을 0으로 만들고 환급 기록 · 원장 BALANCE_RETURN을 남긴다.
     * 그사이 입금이 들어왔거나 이미 처리했으면(두 번 누름) 잔액이 달라 409로 거절한다.
     *
     * @param returnedBy 처리한 관리자(회원 ID)
     */
    @Transactional
    public BalanceReturnResult returnBalance(Long walletId, long amount, Long returnedBy) {
        Wallet wallet = walletRepository.findWithLockById(walletId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));
        if (!wallet.isFrozen()) {
            throw new BusinessException(PaymentErrorCode.WALLET_NOT_FROZEN);
        }
        if (wallet.getBalance() != amount) {
            throw new BusinessException(PaymentErrorCode.BALANCE_CHANGED);
        }
        LocalDateTime now = LocalDateTime.now();
        BalanceReturn balanceReturn = balanceReturnRepository.save(BalanceReturn.of(wallet, amount, returnedBy));
        wallet.withdraw(amount);
        WalletLedger ledger = walletLedgerRepository.save(WalletLedger.record(
            wallet.getId(), WalletLedgerType.BALANCE_RETURN, amount, wallet.getBalance(), balanceReturn.getId()));
        PaymentActionLog.record(PaymentAction.BALANCE_RETURNED,
            "memberId={} walletId={} balanceReturnId={} amount={} balance={} ledgerId={} returnedBy={}",
            wallet.getMemberId(), wallet.getId(), balanceReturn.getId(), amount, wallet.getBalance(), ledger.getId(),
            returnedBy);
        return new BalanceReturnResult(balanceReturn.getId(), wallet.getId(), wallet.getMemberId(), amount,
            wallet.getBalance(), ledger.getId(), now);
    }

    /**
     * 관리자 지갑 목록. 최신순(ID 내림차순)이다.
     *
     * @param frozenOnly true면 동결된 지갑만
     * @param withBalanceOnly true면 잔액이 0보다 큰 지갑만
     */
    @Transactional(readOnly = true)
    public Page<WalletSummary> getWallets(boolean frozenOnly, boolean withBalanceOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Wallet> wallets;
        if (frozenOnly && withBalanceOnly) {
            wallets = walletRepository.findAllByFrozenAtIsNotNullAndBalanceGreaterThanOrderByIdDesc(0L, pageable);
        } else if (frozenOnly) {
            wallets = walletRepository.findAllByFrozenAtIsNotNullOrderByIdDesc(pageable);
        } else if (withBalanceOnly) {
            wallets = walletRepository.findAllByBalanceGreaterThanOrderByIdDesc(0L, pageable);
        } else {
            wallets = walletRepository.findAllByOrderByIdDesc(pageable);
        }
        return wallets.map(WalletSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<WalletLedgerEntry> getLedgers(Long memberId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return walletRepository.findByMemberId(memberId)
            .map(wallet -> walletLedgerRepository.findAllByWalletIdOrderByIdDesc(wallet.getId(), pageable)
                .map(WalletLedgerEntry::from))
            .orElse(Page.empty(pageable));
    }
}
