package com.bbcc.kidly.boundedcontext.review.app.port.in;

import java.time.LocalDateTime;

/**
 * 주문상품 구매확정을 리뷰 작성 자격으로 남긴다. 받는 쪽(in/event)이 이벤트를 이 값으로 바꿔 부른다.
 * 같은 주문상품이 다시 와도(중복 · 동시 수신) 자격은 1건이고 예외 없이 끝난다.
 */
public interface ReviewEligibilityRecorder {

    void record(PurchaseConfirmed confirmed);

    /**
     * @param orderId 주문 ID. 없으면 null
     * @param optionId 옵션 ID. 없으면 null
     * @param size 사이즈. 없으면 null
     */
    record PurchaseConfirmed(
        Long orderItemId,
        Long orderId,
        Long memberId,
        Long productId,
        Long optionId,
        String size,
        LocalDateTime confirmedAt) {
    }
}
