package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class WalletLedgerRepositoryIntegrationTest {

    @Autowired
    private WalletLedgerRepository walletLedgerRepository;

    @Test
    @DisplayName("지갑의 원장만 최신순(ID 내림차순)으로 페이지 조회한다")
    void findAllByWalletIdOrderByIdDesc() {
        WalletLedger first = walletLedgerRepository.save(ledger(WALLET_ID, 1L));
        walletLedgerRepository.save(ledger(20L, 2L));
        WalletLedger second = walletLedgerRepository.save(ledger(WALLET_ID, 3L));
        WalletLedger third = walletLedgerRepository.save(ledger(WALLET_ID, 4L));

        Page<WalletLedger> page =
            walletLedgerRepository.findAllByWalletIdOrderByIdDesc(WALLET_ID, PageRequest.of(0, 2));

        assertThat(page.getContent()).extracting(WalletLedger::getId).containsExactly(third.getId(), second.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
        assertThat(walletLedgerRepository.findAllByWalletIdOrderByIdDesc(WALLET_ID, PageRequest.of(1, 2)).getContent())
            .extracting(WalletLedger::getId).containsExactly(first.getId());
    }

    private static WalletLedger ledger(Long walletId, Long refId) {
        return WalletLedger.record(walletId, WalletLedgerType.CHARGE, 10_000L, 10_000L, refId);
    }
}
