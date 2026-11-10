package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.WithdrawalBlocker;
import com.bbcc.kidly.boundedcontext.member.domain.MemberPolicy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberPolicyTest {

    @Test
    @DisplayName("판매자 탈퇴를 막는 이유를 모두 한 문장에 적고, 모르는 이유는 일반 이름으로 적는다")
    void withdrawalBlockedDetail() {
        var blockers = List.of(new WithdrawalBlocker("UNCONFIRMED_ORDER_ITEM", 3),
            new WithdrawalBlocker("UNSETTLED_ITEM", 2), new WithdrawalBlocker("UNPAID_SETTLEMENT", 1),
            new WithdrawalBlocker("NEW_REASON", 4));

        assertThat(MemberPolicy.withdrawalBlockedDetail(blockers)).isEqualTo(
            "구매확정 전 주문상품 3건, 정산 전 항목 2건, 지급되지 않은 정산 1건, 확인이 필요한 항목 4건이 남아 있어 탈퇴할 수 없어요.");
    }
}
