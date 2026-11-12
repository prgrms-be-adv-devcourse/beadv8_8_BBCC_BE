package com.bbcc.kidly.boundedcontext.order.in.web.internal;

import com.bbcc.kidly.shared.order.dto.MemberOrderSummary;
import com.bbcc.kidly.shared.order.out.MemberOrderHistory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 사이 전용 회원 최근 주문 조회(#1191, 챗봇). 주문 계약 {@link MemberOrderHistory}(shared)를 같은 모양
 * ({@link MemberOrderSummary} 배열)으로 HTTP에 낸다. 사용자 인증 헤더를 요구하지 않는다: 밖에서는 Gateway가 /internal/**을 막는다.
 */
@RestController
@RequiredArgsConstructor
public class MemberOrderController {

    public static final String MEMBER_ORDERS_PATH = "/internal/members/{memberId}/orders";

    private final MemberOrderHistory memberOrderHistory;

    /**
     * 200 + 최근 주문부터 최대 limit개(기본 5, 없으면 []). 회원 ID가 숫자가 아니거나 1 미만, limit이 1~10 밖이면 400.
     */
    @GetMapping(MEMBER_ORDERS_PATH)
    public List<MemberOrderSummary> recent(
        @PathVariable @Positive(message = "회원 ID는 1 이상이어야 합니다.") Long memberId,
        @RequestParam(defaultValue = "5")
        @Min(value = 1, message = "가져올 수는 1 이상 10 이하여야 합니다.")
        @Max(value = 10, message = "가져올 수는 1 이상 10 이하여야 합니다.") int limit) {
        return memberOrderHistory.recent(memberId, limit);
    }
}
