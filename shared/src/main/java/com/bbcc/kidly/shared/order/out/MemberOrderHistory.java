package com.bbcc.kidly.shared.order.out;

import com.bbcc.kidly.shared.order.dto.MemberOrderSummary;
import java.util.List;

/**
 * 회원의 최근 주문 · 배송 조회(#1191, 챗봇). 주문 도메인이 구현한다. 배송지 · 연락처 · 송장번호 · 금액 · 자녀 정보는 주지 않는다.
 * 서비스가 나뉘어 있으면 주문 서비스의 내부 경로 GET /internal/members/{memberId}/orders?limit=가 같은 모양(JSON 배열)으로 답한다
 * (밖에서는 Gateway가 /internal/**을 막는다).
 */
public interface MemberOrderHistory {

    /**
     * 그 회원의 주문을 최근 주문부터 최대 limit개. 주문이 없으면 빈 목록이다.
     *
     * @param memberId 주문한 회원 ID(1 이상)
     * @param limit 1 이상 10 이하
     */
    List<MemberOrderSummary> recent(Long memberId, int limit);
}
