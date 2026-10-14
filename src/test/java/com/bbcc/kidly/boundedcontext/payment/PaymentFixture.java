package com.bbcc.kidly.boundedcontext.payment;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentItem;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

final class PaymentFixture {

    static final Long MEMBER_ID = 1L;
    static final Long WALLET_ID = 10L;
    static final Long ORDER_ID = 100L;
    static final Long CHARGE_ID = 50L;
    static final String TOSS_ORDER_ID = "charge-order-1";
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

    private PaymentFixture() {
    }

    static Wallet wallet(long balance) {
        Wallet wallet = Wallet.create(MEMBER_ID);
        if (balance > 0) {
            wallet.deposit(balance);
        }
        return wallet;
    }

    // 저장된 지갑처럼 ID가 WALLET_ID인 지갑
    static Wallet savedWallet(long balance) {
        Wallet wallet = wallet(balance);
        ReflectionTestUtils.setField(wallet, "id", WALLET_ID);
        return wallet;
    }

    static Charge charge(long amount) {
        return Charge.request(WALLET_ID, TOSS_ORDER_ID, amount, NOW.plusMinutes(30));
    }

    // 저장된 충전처럼 ID가 CHARGE_ID이고 만료가 먼 충전
    static Charge savedCharge(long amount) {
        Charge charge = Charge.request(WALLET_ID, TOSS_ORDER_ID, amount, LocalDateTime.now().plusMinutes(30));
        ReflectionTestUtils.setField(charge, "id", CHARGE_ID);
        return charge;
    }

    static Charge approvedCharge(long amount) {
        Charge charge = charge(amount);
        charge.startConfirming(amount, NOW);
        charge.approve("encrypted-payment-key", NOW);
        return charge;
    }

    static Payment completedPayment() {
        return Payment.complete(ORDER_ID, WALLET_ID, MEMBER_ID, List.of(
            PaymentItem.of(1000L, 10_000L),
            PaymentItem.of(1001L, 5_000L)), NOW);
    }
}
