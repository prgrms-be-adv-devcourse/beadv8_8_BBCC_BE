package com.bbcc.kidly.boundedcontext.payment;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import java.time.LocalDateTime;

final class PaymentFixture {

    static final Long MEMBER_ID = 1L;
    static final Long WALLET_ID = 10L;
    static final Long ORDER_ID = 100L;
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

    static Charge charge(long amount) {
        return Charge.request(WALLET_ID, "charge-order-1", amount, NOW.plusMinutes(30));
    }

    static Charge approvedCharge(long amount) {
        Charge charge = charge(amount);
        charge.startConfirming(amount, NOW);
        charge.approve("encrypted-payment-key", NOW);
        return charge;
    }
}
