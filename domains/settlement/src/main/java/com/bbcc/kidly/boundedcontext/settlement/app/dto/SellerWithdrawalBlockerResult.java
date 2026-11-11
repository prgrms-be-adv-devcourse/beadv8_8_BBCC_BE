package com.bbcc.kidly.boundedcontext.settlement.app.dto;

import com.bbcc.kidly.boundedcontext.settlement.domain.SellerWithdrawalBlocker;

public record SellerWithdrawalBlockerResult(String type, long count) {

    public static SellerWithdrawalBlockerResult from(SellerWithdrawalBlocker blocker) {
        return new SellerWithdrawalBlockerResult(blocker.type().name(), blocker.count());
    }
}
