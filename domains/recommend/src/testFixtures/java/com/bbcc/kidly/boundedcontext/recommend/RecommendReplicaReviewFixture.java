package com.bbcc.kidly.boundedcontext.recommend;

import com.bbcc.kidly.shared.community.event.ReviewChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 리뷰 복사본 테스트에 쓰는 리뷰 변경됨 이벤트를 만든다. 기본 리뷰는 주문상품 {@code ORDER_ITEM_ID}로 산 상품 {@code PRODUCT_ID}의 리뷰다.
 */
final class RecommendReplicaReviewFixture {

    static final Long REVIEW_ID = 501L;

    static final Long ORDER_ITEM_ID = 601L;

    static final Long PRODUCT_ID = 31L;

    /**
     * 리뷰 변경됨이 일어난 시각. 늦고 이른 이벤트는 이 시각에서 분을 더하거나 빼서 만든다.
     */
    static final LocalDateTime REVIEW_CHANGED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);

    private static final int RATING = 4;

    private RecommendReplicaReviewFixture() {
    }

    /**
     * 기본 리뷰의 핏 · 공개 여부 · 발생 시각만 바꾼 리뷰 변경됨이다. 이벤트 ID는 새로 만든다.
     */
    static ReviewChangedEvent reviewChangedEventAt(String fit, boolean visible, LocalDateTime occurredAt) {
        return reviewChangedEvent(REVIEW_ID, ORDER_ITEM_ID, PRODUCT_ID, fit, visible, occurredAt);
    }

    /**
     * 리뷰 · 주문상품 · 상품을 정한 리뷰 변경됨이다. 주문상품이나 상품별로 리뷰를 여럿 넣을 때 쓴다.
     */
    static ReviewChangedEvent reviewChangedEvent(Long reviewId, Long orderItemId, Long productId, String fit,
        boolean visible, LocalDateTime occurredAt) {
        return new ReviewChangedEvent(UUID.randomUUID(), occurredAt,
            new ReviewChangedEvent.Data(reviewId, orderItemId, productId, RATING, fit, visible));
    }
}
