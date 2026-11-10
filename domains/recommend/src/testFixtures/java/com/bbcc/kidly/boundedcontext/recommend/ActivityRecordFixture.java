package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_120_OPTION_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.shared.order.event.CartItemAddedEvent;
import com.bbcc.kidly.shared.order.event.CartItemRemovedEvent;
import com.bbcc.kidly.shared.search.event.SearchPerformedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 활동 기록 테스트가 쓰는 값이다. 회원 1이 "맨투맨"을 카테고리 52, 색상 3 · 5로 찾아 12개가 나왔고, 그 뒤 장바구니에 담았다.
 */
public final class ActivityRecordFixture {

    public static final UUID SEARCH_EVENT_ID = UUID.fromString("5a1c0000-0000-0000-0000-000000000001");

    public static final String KEYWORD = "맨투맨";

    public static final Map<String, List<Long>> ATTRIBUTE_VALUE_IDS = Map.of("COLOR", List.of(3L, 5L));

    public static final long RESULT_COUNT = 12L;

    public static final LocalDateTime SEARCHED_AT = LocalDateTime.of(2026, 11, 3, 10, 0);

    public static final UUID CART_EVENT_ID = UUID.fromString("ca7a0000-0000-0000-0000-000000000001");

    public static final Long CART_ITEM_ID = 201L;

    public static final int ADDED_QTY = 2;

    public static final int CART_QTY = 3;

    public static final LocalDateTime ADDED_AT = LocalDateTime.of(2026, 11, 3, 10, 5);

    public static final UUID REMOVED_EVENT_ID = UUID.fromString("ca7a0000-0000-0000-0000-000000000002");

    public static final int REMOVED_QTY = 3;

    public static final LocalDateTime REMOVED_AT = LocalDateTime.of(2026, 11, 3, 10, 20);

    public static final UUID VIEW_EVENT_ID = UUID.fromString("71e30000-0000-0000-0000-000000000001");

    public static final LocalDateTime VIEWED_AT = LocalDateTime.of(2026, 11, 3, 10, 2);

    public static final UUID WITHDRAWN_EVENT_ID = UUID.fromString("3e3b0000-0000-0000-0000-000000000001");

    public static final LocalDateTime WITHDRAWN_AT = LocalDateTime.of(2026, 11, 4, 9, 0);

    private ActivityRecordFixture() {
    }

    public static ActivityRecord.SearchInfo search() {
        return new ActivityRecord.SearchInfo(SEARCH_EVENT_ID, MEMBER_1_ID, KEYWORD, PRODUCT_A_CATEGORY_ID,
            ATTRIBUTE_VALUE_IDS, RESULT_COUNT, SEARCHED_AT);
    }

    // 회원 1이 자녀 11을 고르고 상품 31의 옵션 32를 2개 담아 그 줄이 3개가 됐다
    public static ActivityRecord.CartItemAddedInfo cartItemAdded() {
        return cartItemAddedFor(CHILD_1_ID);
    }

    public static ActivityRecord.CartItemAddedInfo cartItemAddedFor(Long childProfileId) {
        return new ActivityRecord.CartItemAddedInfo(CART_EVENT_ID, CART_ITEM_ID, MEMBER_1_ID, childProfileId,
            PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, ADDED_QTY, CART_QTY, ADDED_AT);
    }

    public static ActivityRecord.CartItemRemovedInfo cartItemRemoved() {
        return cartItemRemovedFor(CHILD_1_ID, PRODUCT_A_ID);
    }

    public static ActivityRecord.CartItemRemovedInfo cartItemRemovedFor(Long childProfileId, Long productId) {
        return new ActivityRecord.CartItemRemovedInfo(REMOVED_EVENT_ID, CART_ITEM_ID, MEMBER_1_ID, childProfileId,
            productId, PRODUCT_A_120_OPTION_ID, REMOVED_QTY, REMOVED_AT);
    }

    public static ActivityRecord.ProductViewedInfo productViewedFor(Long childProfileId) {
        return new ActivityRecord.ProductViewedInfo(VIEW_EVENT_ID, MEMBER_1_ID, childProfileId, PRODUCT_A_ID,
            VIEWED_AT);
    }

    public static SearchPerformedEvent searchPerformedEvent() {
        return new SearchPerformedEvent(SEARCH_EVENT_ID, SEARCHED_AT, new SearchPerformedEvent.Data(MEMBER_1_ID,
            KEYWORD, PRODUCT_A_CATEGORY_ID, ATTRIBUTE_VALUE_IDS, RESULT_COUNT));
    }

    public static CartItemAddedEvent cartItemAddedEvent() {
        return new CartItemAddedEvent(CART_EVENT_ID, ADDED_AT, new CartItemAddedEvent.Data(CART_ITEM_ID,
            MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, ADDED_QTY, CART_QTY));
    }

    public static CartItemRemovedEvent cartItemRemovedEvent() {
        return cartItemRemovedEventFor(CHILD_1_ID, PRODUCT_A_ID);
    }

    public static CartItemRemovedEvent cartItemRemovedEventFor(Long childId, Long productId) {
        return new CartItemRemovedEvent(REMOVED_EVENT_ID, REMOVED_AT, new CartItemRemovedEvent.Data(CART_ITEM_ID,
            MEMBER_1_ID, childId, productId, PRODUCT_A_120_OPTION_ID, REMOVED_QTY));
    }

    public static MemberWithdrawnEvent memberWithdrawnEvent(Long memberId) {
        return new MemberWithdrawnEvent(WITHDRAWN_EVENT_ID, WITHDRAWN_AT, new MemberWithdrawnEvent.Data(memberId));
    }
}
