package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.QUICK_VIEWED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 담기 팝업을 연 것(팝업 조회)의 활동 기록을 실제 DB에 저장하는 규칙을 확인한다.
 */
@IntegrationTest
class ProductQuickViewRecordIntegrationTest {

    // 저장된 기록 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @ParameterizedTest(name = "자녀 ID {0}")
    @MethodSource("childProfileIds")
    @DisplayName("PREF-22 같은 회원 · 자녀(없으면 회원) · 상품의 팝업을 같은 날 다시 열면 처음 연 것만 기록으로 남긴다")
    void keepFirstQuickViewOfSameDay(Long childProfileId) {
        LocalDateTime first = QUICK_VIEWED_AT.withHour(0).withMinute(0);
        boolean firstSaved = saveIfAbsent(quickViewed(childProfileId, PRODUCT_A_ID, first));

        boolean secondSaved = saveIfAbsent(
            quickViewed(childProfileId, PRODUCT_A_ID, QUICK_VIEWED_AT.withHour(23).withMinute(59)));

        assertThat(firstSaved).isTrue();
        assertThat(secondSaved).isFalse();
        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.PRODUCT_QUICK_VIEWED, MEMBER_1_ID, childProfileId, PRODUCT_A_ID, first);
    }

    static Stream<Long> childProfileIds() {
        return Stream.of(CHILD_1_ID, null);
    }

    // 저장소의 조건부 INSERT는 수정 쿼리라 트랜잭션 안에서 불러야 한다
    private boolean saveIfAbsent(ActivityRecord record) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
            status -> activityRecordRepository.saveProductQuickViewedIfAbsent(record)));
    }

    private static ActivityRecord quickViewed(Long childProfileId, Long productId, LocalDateTime occurredAt) {
        return ActivityRecord.productQuickViewed(new ActivityRecord.ProductViewedInfo(
            UUID.randomUUID(), MEMBER_1_ID, childProfileId, productId, occurredAt));
    }
}
