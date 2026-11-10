package com.bbcc.kidly.boundedcontext.settlement.in.dto;

import com.bbcc.kidly.boundedcontext.settlement.app.dto.SellerWithdrawalBlockerResult;
import java.util.List;

/**
 * @param blockers 탈퇴를 막는 사유(type: UNSETTLED_ITEM · UNPAID_SETTLEMENT). 없으면 빈 목록
 */
public record SellerWithdrawalBlockersResponse(List<Blocker> blockers) {

    public static SellerWithdrawalBlockersResponse from(List<SellerWithdrawalBlockerResult> results) {
        return new SellerWithdrawalBlockersResponse(
            results.stream().map(result -> new Blocker(result.type(), result.count())).toList());
    }

    public record Blocker(String type, long count) {
    }
}
