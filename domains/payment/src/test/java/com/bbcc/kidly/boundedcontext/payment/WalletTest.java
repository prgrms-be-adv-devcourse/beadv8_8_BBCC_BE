package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WalletTest {

    @Test
    @DisplayName("새 지갑은 잔액이 0이다")
    void newWalletHasZeroBalance() {
        Wallet wallet = Wallet.create(PaymentFixture.MEMBER_ID);

        assertThat(wallet.getMemberId()).isEqualTo(PaymentFixture.MEMBER_ID);
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("입금하면 잔액이 늘어난다")
    void depositIncreasesBalance() {
        Wallet wallet = PaymentFixture.wallet(1_000L);

        wallet.deposit(500L);

        assertThat(wallet.getBalance()).isEqualTo(1_500L);
    }

    @Test
    @DisplayName("출금하면 잔액이 줄어든다")
    void withdrawDecreasesBalance() {
        Wallet wallet = PaymentFixture.wallet(1_000L);

        wallet.withdraw(1_000L);

        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("잔액보다 많이 출금하면 잔액 부족 예외가 나고 잔액은 그대로다")
    void withdrawMoreThanBalanceFails() {
        Wallet wallet = PaymentFixture.wallet(1_000L);

        assertThatThrownBy(() -> wallet.withdraw(1_001L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.INSUFFICIENT_BALANCE);
        assertThat(wallet.getBalance()).isEqualTo(1_000L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("0 이하 금액으로는 입금할 수 없다")
    void depositRequiresPositiveAmount(long amount) {
        Wallet wallet = PaymentFixture.wallet(0L);

        assertThatThrownBy(() -> wallet.deposit(amount))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_AMOUNT);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("0 이하 금액으로는 출금할 수 없다")
    void withdrawRequiresPositiveAmount(long amount) {
        Wallet wallet = PaymentFixture.wallet(1_000L);

        assertThatThrownBy(() -> wallet.withdraw(amount))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("잔액이 금액 이상이면 출금할 수 있다")
    void canWithdraw() {
        Wallet wallet = PaymentFixture.wallet(1_000L);

        assertThat(wallet.canWithdraw(1_000L)).isTrue();
        assertThat(wallet.canWithdraw(1_001L)).isFalse();
    }

    @Test
    @DisplayName("동결하면 동결 시각을 남기고 잔액은 그대로다")
    void freeze() {
        Wallet wallet = PaymentFixture.wallet(5_000L);

        boolean frozen = wallet.freeze(NOW);

        assertThat(frozen).isTrue();
        assertThat(wallet.isFrozen()).isTrue();
        assertThat(wallet.getFrozenAt()).isEqualTo(NOW);
        assertThat(wallet.getBalance()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("이미 동결된 지갑을 다시 동결하면 false이고 처음 동결한 시각을 그대로 둔다")
    void freezeTwice() {
        Wallet wallet = PaymentFixture.wallet(0L);
        wallet.freeze(NOW);

        boolean frozen = wallet.freeze(NOW.plusDays(1));

        assertThat(frozen).isFalse();
        assertThat(wallet.getFrozenAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("동결된 지갑에도 입금할 수 있다")
    void depositAfterFreeze() {
        Wallet wallet = PaymentFixture.wallet(1_000L);
        wallet.freeze(NOW);

        wallet.deposit(2_000L);

        assertThat(wallet.getBalance()).isEqualTo(3_000L);
    }
}
