package com.bbcc.kidly.boundedcontext.wish.app.dto;

import com.bbcc.kidly.boundedcontext.wish.domain.WishPurchase;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 조르기 타임라인(#702 · #999). 새 이력 없이 조르기의 시각 · 원인과 조르기로 산 것으로 조립한다. 단계는 순서 있는 배열이다:
 * SENT → PAID 또는 REJECTED 또는 CANCELLED. 처리 전이면 PAID가 WAITING이다(다음 단계를 보인다).
 * 조르기는 주문을 모르므로(#999) 주문 단계는 붙이지 않고 산 주문 ID만 준다(주문 화면은 주문 타임라인으로 본다).
 *
 * @param status 조르기 상태(REQUESTED · PAID · REJECTED · CANCELLED)
 * @param cancelReason 철회 원인. 철회가 아니면 null
 * @param cancelledBy 직접 철회한 사람 종류(보낸 사람, MEMBER · CHILD, #968). 직접 철회가 아니면 null
 * @param rejectReason 거절이 아니면 null
 * @param orderIds 조르기로 산 주문 ID(먼저 산 것부터, 겹치지 않게)
 */
public record WishTimelineView(
    Long requestId,
    String status,
    String rejectReason,
    String cancelReason,
    List<Long> orderIds,
    List<Step> steps,
    String cancelledBy) {

    public static final String DONE = "DONE";
    public static final String WAITING = "WAITING";

    /**
     * @param status DONE · WAITING
     * @param at DONE이면 완료 시각, 그 밖 null
     */
    public record Step(String step, String status, LocalDateTime at) {
    }

    public static WishTimelineView of(WishRequest request, List<WishPurchase> purchases) {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("SENT", DONE, request.getRequestedAt()));
        switch (request.getStatus()) {
            case REQUESTED -> steps.add(new Step("PAID", WAITING, null));
            case PAID -> steps.add(new Step("PAID", DONE, request.getPaidAt()));
            case REJECTED -> steps.add(new Step("REJECTED", DONE, request.getRejectedAt()));
            case CANCELLED -> steps.add(new Step("CANCELLED", DONE, request.getCancelledAt()));
        }
        boolean direct = request.getCancelReason() == WishRequest.CancelReason.DIRECT;
        return new WishTimelineView(request.getId(), request.getStatus().name(),
            request.getRejectReason() == null ? null : request.getRejectReason().name(),
            request.getCancelReason() == null ? null : request.getCancelReason().name(),
            purchases.stream().map(WishPurchase::getOrderId).distinct().toList(), List.copyOf(steps),
            direct ? request.getRequestedByType().name() : null);
    }
}
