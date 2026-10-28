package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WalletLedgerTest {

    @ParameterizedTest
    @CsvSource({
        "CHARGE, 1000",
        "REFUND, 1000",
        "SETTLEMENT, 1000",
        "CHARGE_CANCEL_REVERT, 1000",
        "CHARGE_CANCEL, -1000",
        "PAYMENT, -1000"
    })
    @DisplayName("입금 거래는 양수, 출금 거래는 음수 금액으로 기록된다")
    void amountSignFollowsType(WalletLedgerType type, long expectedAmount) {
        WalletLedger ledger = WalletLedger.record(PaymentFixture.WALLET_ID, type, 1_000L, 5_000L, 7L);

        assertThat(ledger.getAmount()).isEqualTo(expectedAmount);
        assertThat(ledger.getBalanceAfter()).isEqualTo(5_000L);
        assertThat(ledger.getRefId()).isEqualTo(7L);
    }
}
