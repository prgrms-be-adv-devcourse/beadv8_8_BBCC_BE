package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.app.ChargeConfirmService;
import com.bbcc.kidly.boundedcontext.payment.app.ChargeService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeRequestResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ConfirmChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentKeyCipher;
import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 충전 요청 → 승인(FakePaymentGateway) → 입금 · 원장 · 암호화된 paymentKey가 실제 DB에 남는지 확인한다.
 */
@IntegrationTest
class ChargeConfirmIntegrationTest {

    private static final String PAYMENT_KEY = "tgen_20261014120000abcdefghij";
    // 토스 승인 오류 코드 중 51자인 것
    private static final String LONG_CODE = "NOT_SUPPORTED_MONTHLY_INSTALLMENT_PLAN_BELOW_AMOUNT";

    @Autowired
    private ChargeService chargeService;

    @Autowired
    private ChargeConfirmService chargeConfirmService;

    @Autowired
    private PaymentKeyCipher paymentKeyCipher;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JpaWalletRepository jpaWalletRepository;

    @Autowired
    private ChargeRepository chargeRepository;

    @Autowired
    private JpaWalletLedgerRepository jpaWalletLedgerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("승인되면 잔액이 늘고 원장에 CHARGE가 남으며 paymentKey는 암호화돼 저장된다")
    void approve() {
        Wallet wallet = walletRepository.save(PaymentFixture.wallet(5_000L));
        ChargeRequestResult requested = chargeService.request(MEMBER_ID, new ChargeRequest(10_000L));

        chargeConfirmService.confirm(MEMBER_ID,
            new ConfirmChargeRequest(PAYMENT_KEY, requested.orderId(), 10_000L));

        assertThat(jpaWalletRepository.findById(wallet.getId())).get()
            .extracting(Wallet::getBalance).isEqualTo(15_000L);
        assertThat(chargeRepository.findById(requested.chargeId())).get().satisfies(charge -> {
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.DONE);
            assertThat(charge.getApprovedAt()).isNotNull();
            assertThat(charge.getPaymentKeyEnc()).isNotEqualTo(PAYMENT_KEY);
            assertThat(paymentKeyCipher.decrypt(charge.getPaymentKeyEnc())).isEqualTo(PAYMENT_KEY);
        });
        assertThat(jpaWalletLedgerRepository.findAll()).singleElement().satisfies(ledger -> {
            assertThat(ledger.getType()).isEqualTo(WalletLedgerType.CHARGE);
            assertThat(ledger.getAmount()).isEqualTo(10_000L);
            assertThat(ledger.getBalanceAfter()).isEqualTo(15_000L);
            assertThat(ledger.getRefId()).isEqualTo(requested.chargeId());
        });
    }

    @Test
    @DisplayName("50자가 넘는 토스 오류 코드로 승인이 실패해도 실패 상태와 코드를 저장한다")
    void failWithLongCode() {
        walletRepository.save(PaymentFixture.wallet(0L));
        ChargeRequestResult requested = chargeService.request(MEMBER_ID, new ChargeRequest(10_000L));
        chargeService.startConfirming(MEMBER_ID, new ConfirmChargeRequest(PAYMENT_KEY, requested.orderId(), 10_000L));

        chargeService.fail(requested.chargeId(), LONG_CODE);

        assertThat(chargeRepository.findById(requested.chargeId())).get().satisfies(charge -> {
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.FAILED);
            assertThat(charge.getFailureCode()).isEqualTo(LONG_CODE);
        });
    }

    @Test
    @DisplayName("50자가 넘는 토스 오류 코드로 충전 취소가 거절돼도 완료 상태로 되돌리고 코드를 저장한다")
    void failCancelWithLongCode() {
        walletRepository.save(PaymentFixture.wallet(0L));
        ChargeRequestResult requested = chargeService.request(MEMBER_ID, new ChargeRequest(10_000L));
        chargeConfirmService.confirm(MEMBER_ID, new ConfirmChargeRequest(PAYMENT_KEY, requested.orderId(), 10_000L));
        chargeService.startCancel(MEMBER_ID, requested.chargeId());

        chargeService.failCancel(requested.chargeId(), LONG_CODE);

        assertThat(chargeRepository.findById(requested.chargeId())).get()
            .extracting(Charge::getStatus).isEqualTo(ChargeStatus.DONE);
        assertThat(jpaChargeCancellationCodes()).containsExactly(LONG_CODE);
    }

    private List<String> jpaChargeCancellationCodes() {
        return jdbcTemplate.queryForList("select failure_code from payment.charge_cancellation", String.class);
    }
}
