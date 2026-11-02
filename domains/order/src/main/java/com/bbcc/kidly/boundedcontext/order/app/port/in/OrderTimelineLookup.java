package com.bbcc.kidly.boundedcontext.order.app.port.in;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 다른 컨텍스트(조르기)가 주문 처리 타임라인을 읽는다(#702 · #641). 구매자 주문 타임라인(#352)과 같은 조립이고 단계 · 상태 · 시각만 내준다.
 * 구현은 주문 app이고 부르는 쪽 트랜잭션에 합류한다.
 */
public interface OrderTimelineLookup {

    /**
     * @param memberId 주문한 회원. 그 회원의 주문이 아니면 빈 값
     */
    Optional<TimelineView> findTimeline(Long memberId, Long orderId);

    /**
     * @param status 주문 상태 이름(PENDING_PAYMENT · PAID · CANCELLED)
     * @param steps 순서 있는 단계(CREATED → PAYMENT_REQUESTED → PAID → (CANCELLED) → SHIPPED → DELIVERED → CONFIRMED)
     */
    record TimelineView(Long orderId, String status, List<Step> steps) {
    }

    /**
     * @param status DONE · PARTIAL · WAITING · NOT_APPLICABLE
     * @param at DONE이면 완료 시각, 그 밖 null
     */
    record Step(String step, String status, LocalDateTime at) {
    }
}
