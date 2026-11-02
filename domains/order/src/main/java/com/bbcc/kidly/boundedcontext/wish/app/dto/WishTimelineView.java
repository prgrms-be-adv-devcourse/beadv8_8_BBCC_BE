package com.bbcc.kidly.boundedcontext.wish.app.dto;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishOrders;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 조르기 타임라인(#702). 새 이력 없이 조르기의 시각 · 사유 · 주문 ID로 조립한다. 단계는 순서 있는 배열이다:
 * SENT → APPROVED 또는 REJECTED → (바로 결제한 주문이 있으면) 주문 타임라인 단계 CREATED → PAYMENT_REQUESTED → PAID → (CANCELLED)
 * → SHIPPED → DELIVERED → CONFIRMED(주문 타임라인과 같은 이름 · 상태). 처리 전이면 APPROVED가 WAITING이다(주문 타임라인처럼 다음 단계를 보인다).
 * 장바구니로 옮기기 · 거절은 조르기 단계로 끝난다.
 *
 * @param status 조르기 상태(REQUESTED · APPROVED · REJECTED)
 * @param rejectReason 거절이 아니면 null
 * @param orderId 바로 결제로 만든 주문 ID. 없으면 null
 */
public record WishTimelineView(
    Long requestId,
    String status,
    String checkout,
    String rejectReason,
    Long orderId,
    List<Step> steps) {

    public static final String DONE = "DONE";
    public static final String WAITING = "WAITING";

    /**
     * @param status DONE · PARTIAL · WAITING · NOT_APPLICABLE
     * @param at DONE이면 완료 시각, 그 밖 null
     */
    public record Step(String step, String status, LocalDateTime at) {
    }

    /**
     * @param order 바로 결제한 주문의 타임라인. 주문이 없으면 null
     */
    public static WishTimelineView of(WishRequest request, WishOrders.OrderTimeline order) {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("SENT", DONE, request.getRequestedAt()));
        switch (request.getStatus()) {
            case REQUESTED -> steps.add(new Step("APPROVED", WAITING, null));
            case APPROVED -> steps.add(new Step("APPROVED", DONE, request.getDecidedAt()));
            case REJECTED -> steps.add(new Step("REJECTED", DONE, request.getDecidedAt()));
        }
        if (order != null) {
            order.steps().forEach(step -> steps.add(new Step(step.step(), step.status(), step.at())));
        }
        return new WishTimelineView(request.getId(), request.getStatus().name(),
            request.getCheckout() == null ? null : request.getCheckout().name(),
            request.getRejectReason() == null ? null : request.getRejectReason().name(),
            request.getOrderId(), List.copyOf(steps));
    }
}
