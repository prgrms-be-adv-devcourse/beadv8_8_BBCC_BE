package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletBalanceResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletLedgerEntry;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 예치금 지갑 잔액과 입출금 내역(원장)을 조회한다. 지갑이 없으면 404다.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletLedgerRepository walletLedgerRepository;

    @Transactional(readOnly = true)
    public WalletBalanceResult getBalance(Long memberId) {
        return WalletBalanceResult.from(walletOf(memberId));
    }

    @Transactional(readOnly = true)
    public Page<WalletLedgerEntry> getLedgers(Long memberId, int page, int size) {
        Wallet wallet = walletOf(memberId);
        return walletLedgerRepository.findAllByWalletIdOrderByIdDesc(wallet.getId(), PageRequest.of(page, size))
            .map(WalletLedgerEntry::from);
    }

    private Wallet walletOf(Long memberId) {
        return walletRepository.findByMemberId(memberId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.WALLET_NOT_FOUND));
    }
}
