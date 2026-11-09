package com.bbcc.kidly.boundedcontext.wish.in.web.dto;

import com.bbcc.kidly.boundedcontext.wish.app.dto.WishTimelineView;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 조르기 타임라인(#702 · #999). ID는 문자열(장바구니와 같음). 칸은 추가만 한다.
 *
 * @param status 조르기 상태(REQUESTED · PAID · REJECTED · CANCELLED · EXPIRED)
 * @param rejectReason 거절이 아니면 null
 * @param cancelReason 철회 원인. 철회가 아니면 null
 * @param orderIds 조르기로 산 주문 ID(먼저 산 것부터)
 * @param steps SENT → PAID · REJECTED · CANCELLED · EXPIRED(시스템 만료, #1008)
 * @param cancelledBy 직접 철회한 사람 종류(보낸 사람, MEMBER · CHILD, #968). 직접 철회가 아니면 null
 */
public record WishTimelineResponse(
    String requestId,
    String status,
    String rejectReason,
    String cancelReason,
    List<String> orderIds,
    List<Step> steps,
    String cancelledBy) {

    /**
     * @param status DONE · WAITING
     * @param at DONE이면 완료 시각, 그 밖 null
     */
    public record Step(String step, String status, LocalDateTime at) {
    }

    public static WishTimelineResponse from(WishTimelineView view) {
        return new WishTimelineResponse(String.valueOf(view.requestId()), view.status(), view.rejectReason(),
            view.cancelReason(), view.orderIds().stream().map(String::valueOf).toList(),
            view.steps().stream().map(step -> new Step(step.step(), step.status(), step.at())).toList(),
            view.cancelledBy());
    }
}
