package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.error.BusinessException;

final class Amounts {

    private Amounts() {
    }

    static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new BusinessException(PaymentErrorCode.INVALID_AMOUNT);
        }
    }
}
