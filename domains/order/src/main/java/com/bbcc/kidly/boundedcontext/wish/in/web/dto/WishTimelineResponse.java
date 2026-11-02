package com.bbcc.kidly.boundedcontext.wish.in.web.dto;

import com.bbcc.kidly.boundedcontext.wish.app.dto.WishTimelineView;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 조르기 타임라인(#702). ID는 문자열(장바구니와 같음). 칸은 추가만 한다.
 *
 * @param status 조르기 상태(REQUESTED · APPROVED · REJECTED)
 * @param checkout ORDER · CART. 처리 전 · 거절이면 null
 * @param rejectReason 거절이 아니면 null
 * @param orderId 바로 결제로 만든 주문 ID. 없으면 null
 * @param steps SENT → APPROVED · REJECTED → (주문이 있으면) 주문 타임라인 단계
 */
public record WishTimelineResponse(
    String requestId,
    String status,
    String checkout,
    String rejectReason,
    String orderId,
    List<Step> steps) {

    /**
     * @param status DONE · PARTIAL · WAITING · NOT_APPLICABLE
     * @param at DONE이면 완료 시각, 그 밖 null
     */
    public record Step(String step, String status, LocalDateTime at) {
    }

    public static WishTimelineResponse from(WishTimelineView view) {
        return new WishTimelineResponse(String.valueOf(view.requestId()), view.status(), view.checkout(),
            view.rejectReason(), view.orderId() == null ? null : String.valueOf(view.orderId()),
            view.steps().stream().map(step -> new Step(step.step(), step.status(), step.at())).toList());
    }
}
