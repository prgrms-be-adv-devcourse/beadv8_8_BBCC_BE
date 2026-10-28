package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletBalanceResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletLedgerEntry;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 예치금 지갑 잔액과 입출금 내역(원장)을 조회한다. 지갑이 없으면 아직 쓰지 않은 지갑이라 잔액 0 · 빈 원장이다.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletLedgerRepository walletLedgerRepository;

    @Transactional(readOnly = true)
    public WalletBalanceResult getBalance(Long memberId) {
        return walletRepository.findByMemberId(memberId)
            .map(WalletBalanceResult::from)
            .orElse(new WalletBalanceResult(0L));
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
