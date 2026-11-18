package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.util.List;
import java.util.stream.Stream;

public record SellerWithdrawalBlocker(SellerWithdrawalBlockerType type, long count) {

    public static List<SellerWithdrawalBlocker> of(long unsettledItemCount, long unpaidSettlementCount) {
        return Stream.of(
                new SellerWithdrawalBlocker(SellerWithdrawalBlockerType.UNSETTLED_ITEM, unsettledItemCount),
                new SellerWithdrawalBlocker(SellerWithdrawalBlockerType.UNPAID_SETTLEMENT, unpaidSettlementCount))
            .filter(blocker -> blocker.count() > 0)
            .toList();
    }
}
