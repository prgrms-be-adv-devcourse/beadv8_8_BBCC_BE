package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.payment.app.WalletService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletLedgerEntry;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerRepository walletLedgerRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    @DisplayName("회원의 지갑 잔액을 준다")
    void getBalance() {
        given(walletRepository.findByMemberId(PaymentFixture.MEMBER_ID))
            .willReturn(Optional.of(PaymentFixture.wallet(15_000L)));

        var result = walletService.getBalance(PaymentFixture.MEMBER_ID);

        assertThat(result.balance()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("지갑이 없으면 WALLET_NOT_FOUND")
    void walletNotFound() {
        given(walletRepository.findByMemberId(PaymentFixture.MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getBalance(PaymentFixture.MEMBER_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.WALLET_NOT_FOUND));
    }

    @Test
    @DisplayName("원장은 회원 지갑의 원장을 최신순 페이지로 준다")
    void getLedgers() {
        WalletLedger ledger = WalletLedger.record(WALLET_ID, WalletLedgerType.PAYMENT, 3_000L, 7_000L, 100L);
        ReflectionTestUtils.setField(ledger, "id", 9L);
        given(walletRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(PaymentFixture.savedWallet(7_000L)));
        given(walletLedgerRepository.findAllByWalletIdOrderByIdDesc(WALLET_ID, PageRequest.of(0, 20)))
            .willReturn(new PageImpl<>(List.of(ledger), PageRequest.of(0, 20), 1));

        Page<WalletLedgerEntry> ledgers = walletService.getLedgers(MEMBER_ID, 0, 20);

        assertThat(ledgers.getContent()).containsExactly(
            new WalletLedgerEntry(9L, "PAYMENT", -3_000L, 7_000L, 100L, null));
    }

    @Test
    @DisplayName("지갑이 없으면 원장도 WALLET_NOT_FOUND")
    void ledgersWithoutWallet() {
        given(walletRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getLedgers(MEMBER_ID, 0, 20))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.WALLET_NOT_FOUND));
    }
}
