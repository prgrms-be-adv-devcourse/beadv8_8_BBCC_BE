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
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_120_OPTION_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 활동 기록 서비스가 실제 DB에 검색 · 장바구니 담기 · 빼기를 저장하는 규칙을 확인한다.
 * Kafka로 받는 연결은 RecommendActivityKafkaIntegrationTest가 확인한다.
 */
@IntegrationTest
class ActivityRecordIntegrationTest {

    // 저장된 기록 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

    @Autowired
    private ActivityRecordService activityRecordService;

    @Test
    @DisplayName("검색 이벤트를 활동 기록으로 저장하고 항목 값 ID 묶음을 그대로 읽는다")
    void saveSearch() {
        activityRecordService.recordSearch(ActivityRecordFixture.searchPerformedEvent());

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getMemberId,
                ActivityRecord::getKeyword,
                ActivityRecord::getCategoryId,
                ActivityRecord::getAttributeValueIds,
                ActivityRecord::getResultCount,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.SEARCH, SEARCH_EVENT_ID, MEMBER_1_ID, KEYWORD, PRODUCT_A_CATEGORY_ID,
                ATTRIBUTE_VALUE_IDS, RESULT_COUNT, SEARCHED_AT);
    }

    @Test
    @DisplayName("장바구니 담기 이벤트를 활동 기록으로 저장한다")
    void saveCartItemAdded() {
        activityRecordService.recordCartItemAdded(ActivityRecordFixture.cartItemAddedEvent());

        assertThat(activityRecordRepository.findAll()).singleElement()
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
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.CART_ITEM_ADDED, CART_EVENT_ID, CART_ITEM_ID, MEMBER_1_ID, CHILD_1_ID,
                PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, ADDED_QTY, CART_QTY, ADDED_AT);
    }

    // 자녀를 고르지 않았거나 주문이 상품 정보를 받아 두지 않은 줄을 빼도 활동 기록을 남긴다
    @ParameterizedTest(name = "자녀 ID {0}, 상품 ID {1}")
    @MethodSource("childAndProductIds")
    @DisplayName("장바구니 빼기 이벤트를 활동 기록으로 저장하고 뺄 때의 수량을 장바구니 수량 칸에 둔다")
    void saveCartItemRemoved(Long childId, Long productId) {
        activityRecordService.recordCartItemRemoved(ActivityRecordFixture.cartItemRemovedEventFor(childId, productId));

        assertThat(activityRecordRepository.findAll()).singleElement()
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
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.CART_ITEM_REMOVED, REMOVED_EVENT_ID, CART_ITEM_ID, MEMBER_1_ID, childId,
                productId, PRODUCT_A_120_OPTION_ID, null, REMOVED_QTY, REMOVED_AT);
    }

    static Stream<Arguments> childAndProductIds() {
        return Stream.of(
            Arguments.of(CHILD_1_ID, PRODUCT_A_ID),
            Arguments.of(null, PRODUCT_A_ID),
            Arguments.of(CHILD_1_ID, null),
            Arguments.of(null, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("records")
    @DisplayName("같은 이벤트를 다시 받아도 예외 없이 활동 기록은 한 줄이다")
    void keepOneRecordForSameEvent(Consumer<ActivityRecordService> record) {
        record.accept(activityRecordService);

        assertThatCode(() -> record.accept(activityRecordService)).doesNotThrowAnyException();

        assertThat(activityRecordRepository.findAll()).hasSize(1);
    }

    static Stream<Named<Consumer<ActivityRecordService>>> records() {
        return Stream.of(
            Named.of("검색", service -> service.recordSearch(ActivityRecordFixture.searchPerformedEvent())),
            Named.of("장바구니 담기",
                service -> service.recordCartItemAdded(ActivityRecordFixture.cartItemAddedEvent())),
            Named.of("장바구니 빼기",
                service -> service.recordCartItemRemoved(ActivityRecordFixture.cartItemRemovedEvent())));
    }

    @Test
    @DisplayName("같은 이벤트의 활동 기록을 두 줄 저장하면 UNIQUE 제약이 막는다")
    void rejectSecondRecordOfSameEvent() {
        activityRecordRepository.saveAndFlush(ActivityRecord.searched(ActivityRecordFixture.search()));

        assertThatThrownBy(
            () -> activityRecordRepository.saveAndFlush(ActivityRecord.searched(ActivityRecordFixture.search())))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(activityRecordRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("회원 탈퇴 이벤트로 그 회원의 검색 · 담기 · 빼기 기록을 모두 지우고 다른 회원의 기록은 남긴다")
    void deleteWithdrawnMemberActivities() {
        activityRecordService.recordSearch(ActivityRecordFixture.searchPerformedEvent());
        activityRecordService.recordCartItemAdded(ActivityRecordFixture.cartItemAddedEvent());
        activityRecordService.recordCartItemRemoved(ActivityRecordFixture.cartItemRemovedEvent());
        ActivityRecord others = activityRecordRepository.saveAndFlush(otherMemberSearch());

        activityRecordService.deleteWithdrawnMemberActivities(ActivityRecordFixture.memberWithdrawnEvent(MEMBER_1_ID));

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(ActivityRecord::getId, ActivityRecord::getMemberId)
            .containsExactly(others.getId(), OTHER_MEMBER_ID);
    }

    private static ActivityRecord otherMemberSearch() {
        return ActivityRecord.searched(new ActivityRecord.SearchInfo(UUID.randomUUID(), OTHER_MEMBER_ID, KEYWORD,
            PRODUCT_A_CATEGORY_ID, ATTRIBUTE_VALUE_IDS, RESULT_COUNT, SEARCHED_AT));
    }
}
