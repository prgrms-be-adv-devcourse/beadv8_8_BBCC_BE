package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.payment.in.scheduler.ChargeScheduler;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 충전 정리 스케줄러가 실제 DB의 충전 상태 · 지갑 · 원장을 바꾸는지 확인한다. 토스 조회 결과는 FakePaymentGateway로 정한다.
 */
@IntegrationTest
class ChargeReconcileIntegrationTest {

    @Autowired
    private ChargeScheduler chargeScheduler;

    @Autowired
    private FakePaymentGateway fakePaymentGateway;

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

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = walletRepository.save(Wallet.create(MEMBER_ID));
    }

    @AfterEach
    void resetGateway() {
        fakePaymentGateway.reset();
    }

    @Test
    @DisplayName("5분 넘게 승인 중인 충전은 토스 조회 결과대로 승인 · 실패로 바꾸고, 결과를 모르거나 오래되지 않았으면 그대로 둔다")
    void reconcileConfirming() {
        Charge approved = staleConfirming(10_000L);
        Charge rejected = staleConfirming(20_000L);
        Charge unknown = staleConfirming(30_000L);
        Charge recent = confirming(40_000L);
        fakePaymentGateway.willFindApproved(approved.getTossOrderId(), 10_000L);
        fakePaymentGateway.willFindRejected(rejected.getTossOrderId(), "ABORTED");
        fakePaymentGateway.willFindApproved(recent.getTossOrderId(), 40_000L);

        chargeScheduler.reconcileConfirming();

        assertThat(statusOf(approved)).isEqualTo(ChargeStatus.DONE);
        assertThat(chargeRepository.findById(approved.getId()).orElseThrow().getPaymentKeyEnc()).isNotBlank();
        assertThat(chargeRepository.findById(rejected.getId())).get().satisfies(charge -> {
            assertThat(charge.getStatus()).isEqualTo(ChargeStatus.FAILED);
            assertThat(charge.getFailureCode()).isEqualTo("ABORTED");
        });
        assertThat(statusOf(unknown)).isEqualTo(ChargeStatus.CONFIRMING);
        assertThat(statusOf(recent)).isEqualTo(ChargeStatus.CONFIRMING);
        assertThat(jpaWalletRepository.findById(wallet.getId())).get()
            .extracting(Wallet::getBalance).isEqualTo(10_000L);
        assertThat(jpaWalletLedgerRepository.findAll()).singleElement().satisfies(ledger -> {
            assertThat(ledger.getType()).isEqualTo(WalletLedgerType.CHARGE);
            assertThat(ledger.getRefId()).isEqualTo(approved.getId());
        });
    }

    @Test
    @DisplayName("다시 실행해도 이미 승인한 충전은 다시 입금하지 않는다")
    void reconcileOnce() {
        Charge approved = staleConfirming(10_000L);
        fakePaymentGateway.willFindApproved(approved.getTossOrderId(), 10_000L);

        chargeScheduler.reconcileConfirming();
        chargeScheduler.reconcileConfirming();

        assertThat(jpaWalletRepository.findById(wallet.getId())).get()
            .extracting(Wallet::getBalance).isEqualTo(10_000L);
        assertThat(jpaWalletLedgerRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("만료 시각이 지난 대기 충전만 만료로 바꾼다")
    void expireOverduePending() {
        Charge overdue = chargeRepository.save(
            Charge.request(wallet.getId(), UUID.randomUUID().toString(), 10_000L, LocalDateTime.now().minusMinutes(1)));
        Charge notYet = chargeRepository.save(
            Charge.request(wallet.getId(), UUID.randomUUID().toString(), 10_000L, LocalDateTime.now().plusMinutes(10)));

        chargeScheduler.expireOverduePending();

        assertThat(statusOf(overdue)).isEqualTo(ChargeStatus.EXPIRED);
        assertThat(statusOf(notYet)).isEqualTo(ChargeStatus.PENDING);
    }

    private Charge confirming(long amount) {
        Charge charge = Charge.request(wallet.getId(), UUID.randomUUID().toString(), amount,
            LocalDateTime.now().plusMinutes(30));
        charge.startConfirming(amount, LocalDateTime.now());
        return chargeRepository.save(charge);
    }

    // 승인 중으로 바꾼 지 10분 지난 충전. updated_at은 Auditing이 채우므로 저장한 뒤 직접 되돌린다
    private Charge staleConfirming(long amount) {
        Charge charge = confirming(amount);
        jdbcTemplate.update("update payment.charge set updated_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(10), charge.getId());
        return charge;
    }

    private ChargeStatus statusOf(Charge charge) {
        return chargeRepository.findById(charge.getId()).orElseThrow().getStatus();
    }
}
