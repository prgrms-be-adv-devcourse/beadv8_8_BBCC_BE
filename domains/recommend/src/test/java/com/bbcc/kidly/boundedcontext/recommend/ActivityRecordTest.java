package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.ADDED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.ATTRIBUTE_VALUE_IDS;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.CART_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.CART_ITEM_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.CART_QTY;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.KEYWORD;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.ADDED_QTY;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.REMOVED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.REMOVED_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.REMOVED_QTY;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.RESULT_COUNT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.SEARCHED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.SEARCH_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.VIEWED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.VIEW_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_120_OPTION_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ActivityRecordTest {

    @Test
    @DisplayName("검색 값으로 검색 종류의 활동 기록을 만든다")
    void createSearch() {
        ActivityRecord record = ActivityRecord.searched(ActivityRecordFixture.search());

        assertThat(record)
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getKeyword,
                ActivityRecord::getCategoryId,
                ActivityRecord::getAttributeValueIds,
                ActivityRecord::getResultCount,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.SEARCH, SEARCH_EVENT_ID, MEMBER_1_ID, null, KEYWORD, PRODUCT_A_CATEGORY_ID,
                ATTRIBUTE_VALUE_IDS, RESULT_COUNT, SEARCHED_AT);
    }

    @Test
    @DisplayName("카테고리와 항목 값을 고르지 않은 검색도 활동 기록을 만든다")
    void createSearchWithoutFilters() {
        ActivityRecord record = ActivityRecord.searched(new ActivityRecord.SearchInfo(SEARCH_EVENT_ID, MEMBER_1_ID,
            KEYWORD, null, null, RESULT_COUNT, SEARCHED_AT, null));

        assertThat(record.getCategoryId()).isNull();
        assertThat(record.getAttributeValueIds()).isNull();
        assertThat(record.getKeyword()).isEqualTo(KEYWORD);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("searchesWithNull")
    @DisplayName("검색 값에 필수 값이 없으면 활동 기록을 만들지 않는다")
    void rejectSearchWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> searchesWithNull() {
        return Stream.of(
            Named.of("검색 값", () -> ActivityRecord.searched(null)),
            Named.of("이벤트 ID", () -> ActivityRecord.searched(new ActivityRecord.SearchInfo(
                null, MEMBER_1_ID, KEYWORD, PRODUCT_A_CATEGORY_ID, ATTRIBUTE_VALUE_IDS, RESULT_COUNT, SEARCHED_AT,
                null))),
            Named.of("회원 ID", () -> ActivityRecord.searched(new ActivityRecord.SearchInfo(
                SEARCH_EVENT_ID, null, KEYWORD, PRODUCT_A_CATEGORY_ID, ATTRIBUTE_VALUE_IDS, RESULT_COUNT,
                SEARCHED_AT, null))),
            Named.of("검색어", () -> ActivityRecord.searched(new ActivityRecord.SearchInfo(
                SEARCH_EVENT_ID, MEMBER_1_ID, null, PRODUCT_A_CATEGORY_ID, ATTRIBUTE_VALUE_IDS, RESULT_COUNT,
                SEARCHED_AT, null))),
            Named.of("검색 시각", () -> ActivityRecord.searched(new ActivityRecord.SearchInfo(
                SEARCH_EVENT_ID, MEMBER_1_ID, KEYWORD, PRODUCT_A_CATEGORY_ID, ATTRIBUTE_VALUE_IDS, RESULT_COUNT,
                null, null))));
    }

    // 자녀를 고르지 않은 담기도 회원의 활동이라 남긴다
    @ParameterizedTest(name = "자녀 ID {0}")
    @MethodSource("childProfileIds")
    @DisplayName("장바구니 담기 값으로 담기 종류의 활동 기록을 만든다")
    void createCartItemAdded(Long childProfileId) {
        ActivityRecord record = ActivityRecord.cartItemAdded(ActivityRecordFixture.cartItemAddedFor(childProfileId));

        assertThat(record)
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getCartItemId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getProductOptionId,
                ActivityRecord::getAddedQty,
                ActivityRecord::getCartQty,
                ActivityRecord::getOccurredAt,
                ActivityRecord::getKeyword)
            .containsExactly(ActivityType.CART_ITEM_ADDED, CART_EVENT_ID, CART_ITEM_ID, MEMBER_1_ID, childProfileId,
                PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, ADDED_QTY, CART_QTY, ADDED_AT, null);
    }

    static Stream<Long> childProfileIds() {
        return Stream.of(CHILD_1_ID, null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cartItemsAddedWithNull")
    @DisplayName("장바구니 담기 값에 필수 값이 없으면 활동 기록을 만들지 않는다")
    void rejectCartItemAddedWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> cartItemsAddedWithNull() {
        return Stream.of(
            Named.of("담기 값", () -> ActivityRecord.cartItemAdded(null)),
            Named.of("이벤트 ID", () -> ActivityRecord.cartItemAdded(new ActivityRecord.CartItemAddedInfo(
                null, CART_ITEM_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, ADDED_QTY,
                CART_QTY, ADDED_AT))),
            Named.of("장바구니 줄 ID", () -> ActivityRecord.cartItemAdded(new ActivityRecord.CartItemAddedInfo(
                CART_EVENT_ID, null, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, ADDED_QTY,
                CART_QTY, ADDED_AT))),
            Named.of("회원 ID", () -> ActivityRecord.cartItemAdded(new ActivityRecord.CartItemAddedInfo(
                CART_EVENT_ID, CART_ITEM_ID, null, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, ADDED_QTY,
                CART_QTY, ADDED_AT))),
            Named.of("상품 ID", () -> ActivityRecord.cartItemAdded(new ActivityRecord.CartItemAddedInfo(
                CART_EVENT_ID, CART_ITEM_ID, MEMBER_1_ID, CHILD_1_ID, null, PRODUCT_A_120_OPTION_ID, ADDED_QTY,
                CART_QTY, ADDED_AT))),
            Named.of("옵션 ID", () -> ActivityRecord.cartItemAdded(new ActivityRecord.CartItemAddedInfo(
                CART_EVENT_ID, CART_ITEM_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, null, ADDED_QTY,
                CART_QTY, ADDED_AT))),
            Named.of("담은 시각", () -> ActivityRecord.cartItemAdded(new ActivityRecord.CartItemAddedInfo(
                CART_EVENT_ID, CART_ITEM_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, ADDED_QTY,
                CART_QTY, null))));
    }

    @ParameterizedTest(name = "자녀 ID {0}, 상품 ID {1}")
    @MethodSource("childAndProductIds")
    @DisplayName("장바구니 빼기 값으로 빼기 종류의 활동 기록을 만들고, 뺄 때의 수량은 장바구니 수량 칸에 둔다")
    void createCartItemRemoved(Long childProfileId, Long productId) {
        ActivityRecord record = ActivityRecord.cartItemRemoved(
            ActivityRecordFixture.cartItemRemovedFor(childProfileId, productId));

        assertThat(record)
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getCartItemId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getProductOptionId,
                ActivityRecord::getAddedQty,
                ActivityRecord::getCartQty,
                ActivityRecord::getOccurredAt,
                ActivityRecord::getKeyword)
            .containsExactly(ActivityType.CART_ITEM_REMOVED, REMOVED_EVENT_ID, CART_ITEM_ID, MEMBER_1_ID,
                childProfileId, productId, PRODUCT_A_120_OPTION_ID, null, REMOVED_QTY, REMOVED_AT, null);
    }

    static Stream<Arguments> childAndProductIds() {
        return Stream.of(
            Arguments.of(CHILD_1_ID, PRODUCT_A_ID),
            Arguments.of(null, PRODUCT_A_ID),
            Arguments.of(CHILD_1_ID, null),
            Arguments.of(null, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cartItemsRemovedWithNull")
    @DisplayName("장바구니 빼기 값에 필수 값이 없으면 활동 기록을 만들지 않는다")
    void rejectCartItemRemovedWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> cartItemsRemovedWithNull() {
        return Stream.of(
            Named.of("빼기 값", () -> ActivityRecord.cartItemRemoved(null)),
            Named.of("이벤트 ID", () -> ActivityRecord.cartItemRemoved(new ActivityRecord.CartItemRemovedInfo(
                null, CART_ITEM_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, REMOVED_QTY,
                REMOVED_AT))),
            Named.of("장바구니 줄 ID", () -> ActivityRecord.cartItemRemoved(new ActivityRecord.CartItemRemovedInfo(
                REMOVED_EVENT_ID, null, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, REMOVED_QTY,
                REMOVED_AT))),
            Named.of("회원 ID", () -> ActivityRecord.cartItemRemoved(new ActivityRecord.CartItemRemovedInfo(
                REMOVED_EVENT_ID, CART_ITEM_ID, null, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, REMOVED_QTY,
                REMOVED_AT))),
            Named.of("옵션 ID", () -> ActivityRecord.cartItemRemoved(new ActivityRecord.CartItemRemovedInfo(
                REMOVED_EVENT_ID, CART_ITEM_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, null, REMOVED_QTY,
                REMOVED_AT))),
            Named.of("뺀 시각", () -> ActivityRecord.cartItemRemoved(new ActivityRecord.CartItemRemovedInfo(
                REMOVED_EVENT_ID, CART_ITEM_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID,
                REMOVED_QTY, null))));
    }

    @ParameterizedTest(name = "자녀 ID {0}")
    @MethodSource("childProfileIds")
    @DisplayName("상세 진입 값으로 상세 진입 종류의 활동 기록을 만들고 고른 자녀를 그대로 담으며 장바구니 · 수량 · 검색 칸은 비운다")
    void createProductViewed(Long childProfileId) {
        ActivityRecord record = ActivityRecord.productViewed(ActivityRecordFixture.productViewedFor(childProfileId));

        assertThat(record)
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getOccurredAt,
                ActivityRecord::getCartItemId,
                ActivityRecord::getProductOptionId,
                ActivityRecord::getAddedQty,
                ActivityRecord::getCartQty,
                ActivityRecord::getKeyword)
            .containsExactly(ActivityType.PRODUCT_VIEWED, VIEW_EVENT_ID, MEMBER_1_ID, childProfileId, PRODUCT_A_ID,
                VIEWED_AT, null, null, null, null, null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productViewsWithNull")
    @DisplayName("상세 진입 값에 필수 값이 없으면 활동 기록을 만들지 않는다")
    void rejectProductViewedWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> productViewsWithNull() {
        return Stream.of(
            Named.of("상세 진입 값", () -> ActivityRecord.productViewed(null)),
            Named.of("이벤트 ID", () -> ActivityRecord.productViewed(new ActivityRecord.ProductViewedInfo(
                null, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, VIEWED_AT))),
            Named.of("회원 ID", () -> ActivityRecord.productViewed(new ActivityRecord.ProductViewedInfo(
                VIEW_EVENT_ID, null, CHILD_1_ID, PRODUCT_A_ID, VIEWED_AT))),
            Named.of("상품 ID", () -> ActivityRecord.productViewed(new ActivityRecord.ProductViewedInfo(
                VIEW_EVENT_ID, MEMBER_1_ID, CHILD_1_ID, null, VIEWED_AT))),
            Named.of("진입 시각", () -> ActivityRecord.productViewed(new ActivityRecord.ProductViewedInfo(
                VIEW_EVENT_ID, MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, null))));
    }
}
