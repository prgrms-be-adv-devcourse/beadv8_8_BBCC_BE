package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.payment.app.WalletService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.BalanceReturnResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.WalletLedgerEntry;
import com.bbcc.kidly.boundedcontext.payment.domain.BalanceReturn;
import com.bbcc.kidly.boundedcontext.payment.domain.BalanceReturnRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.error.BusinessException;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private BalanceReturnRepository balanceReturnRepository;

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
    @DisplayName("지갑이 없으면 아직 쓰지 않은 지갑이므로 잔액 0을 준다")
    void balanceWithoutWallet() {
        given(walletRepository.findByMemberId(PaymentFixture.MEMBER_ID)).willReturn(Optional.empty());

        var result = walletService.getBalance(PaymentFixture.MEMBER_ID);

        assertThat(result.balance()).isZero();
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
    @DisplayName("지갑이 없으면 원장은 빈 페이지다")
    void ledgersWithoutWallet() {
        given(walletRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

        Page<WalletLedgerEntry> ledgers = walletService.getLedgers(MEMBER_ID, 0, 20);

        assertThat(ledgers.getContent()).isEmpty();
        assertThat(ledgers.getTotalElements()).isZero();
        assertThat(ledgers.getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("동결된 지갑의 잔액과 요청 금액이 같으면 잔액을 0으로 만들고 환급 기록 · 원장 BALANCE_RETURN(ref_id = 환급 ID)을 남긴다")
    void returnBalance() {
        Wallet wallet = frozenWallet(5_000L);
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(wallet));
        given(balanceReturnRepository.save(any())).willAnswer(invocation -> {
            BalanceReturn saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 40L);
            return saved;
        });
        given(walletLedgerRepository.save(any())).willAnswer(invocation -> {
            WalletLedger saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 41L);
            return saved;
        });

        BalanceReturnResult result = walletService.returnBalance(WALLET_ID, 5_000L, 3L);

        assertThat(wallet.getBalance()).isZero();
        assertThat(result.balanceReturnId()).isEqualTo(40L);
        assertThat(result.ledgerId()).isEqualTo(41L);
        assertThat(result.balance()).isZero();
        ArgumentCaptor<BalanceReturn> record = ArgumentCaptor.forClass(BalanceReturn.class);
        then(balanceReturnRepository).should().save(record.capture());
        assertThat(record.getValue().getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(record.getValue().getAmount()).isEqualTo(5_000L);
        assertThat(record.getValue().getReturnedBy()).isEqualTo(3L);
        ArgumentCaptor<WalletLedger> ledger = ArgumentCaptor.forClass(WalletLedger.class);
        then(walletLedgerRepository).should().save(ledger.capture());
        assertThat(ledger.getValue().getType()).isEqualTo(WalletLedgerType.BALANCE_RETURN);
        assertThat(ledger.getValue().getAmount()).isEqualTo(-5_000L);
        assertThat(ledger.getValue().getBalanceAfter()).isZero();
        assertThat(ledger.getValue().getRefId()).isEqualTo(40L);
    }

    @Test
    @DisplayName("요청 금액이 지금 잔액과 다르면(그사이 입금 · 이미 처리) BALANCE_CHANGED로 거절하고 잔액은 그대로다")
    void returnBalanceMismatch() {
        Wallet wallet = frozenWallet(7_000L);
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.returnBalance(WALLET_ID, 5_000L, 3L))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.BALANCE_CHANGED));
        assertThat(wallet.getBalance()).isEqualTo(7_000L);
        then(balanceReturnRepository).should(never()).save(any());
        then(walletLedgerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("동결되지 않은 지갑은 WALLET_NOT_FROZEN으로 거절한다")
    void returnBalanceNotFrozen() {
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.of(PaymentFixture.savedWallet(5_000L)));

        assertThatThrownBy(() -> walletService.returnBalance(WALLET_ID, 5_000L, 3L))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.WALLET_NOT_FROZEN));
    }

    @Test
    @DisplayName("없는 지갑은 WALLET_NOT_FOUND")
    void returnBalanceWalletMissing() {
        given(walletRepository.findWithLockById(WALLET_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.returnBalance(WALLET_ID, 5_000L, 3L))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.WALLET_NOT_FOUND));
    }

    private static Wallet frozenWallet(long balance) {
        Wallet wallet = PaymentFixture.savedWallet(balance);
        wallet.freeze(PaymentFixture.NOW);
        return wallet;
    }
}
