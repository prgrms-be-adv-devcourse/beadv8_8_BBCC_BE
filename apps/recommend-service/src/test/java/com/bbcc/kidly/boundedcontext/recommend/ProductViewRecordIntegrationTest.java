package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상세 진입 기록을 같은 날 한 번만 저장하는 규칙을 실제 DB에서 확인한다.
 * 같은 날인지는 서비스가 정하는 한국 시간의 날짜로 가르므로, HTTP로는 시각을 정할 수 없는 날짜 경계와 동시 저장을 여기서 본다.
 */
@IntegrationTest
class ProductViewRecordIntegrationTest {

    private static final LocalDate VIEW_DAY = LocalDate.of(2026, 11, 10);

    @Autowired
    private TransactionTemplate transactionTemplate;

    // 저장된 기록 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

    @ParameterizedTest(name = "자녀 ID {0}")
    @MethodSource("childProfileIds")
    @DisplayName("PREF-15 같은 회원 · 자녀 · 상품이 같은 날 다른 시각에 상세 진입을 다시 저장하면 저장하지 않고 false를 돌려준다")
    void skipSecondViewOfSameDay(Long childProfileId) {
        boolean first = saveIfAbsent(childProfileId, VIEW_DAY.atTime(10, 0));
        boolean second = saveIfAbsent(childProfileId, VIEW_DAY.atTime(23, 59, 59));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(ActivityRecord::getType, ActivityRecord::getChildProfileId, ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.PRODUCT_VIEWED, childProfileId, VIEW_DAY.atTime(10, 0));
    }

    static Stream<Long> childProfileIds() {
        return Stream.of(CHILD_1_ID, null);
    }

    // 저장소의 조건부 INSERT는 수정 쿼리라 트랜잭션 안에서 불러야 한다
    private boolean saveIfAbsent(Long childProfileId, LocalDateTime viewedAt) {
        ActivityRecord record = ActivityRecord.productViewed(new ActivityRecord.ProductViewedInfo(
            UUID.randomUUID(), MEMBER_1_ID, childProfileId, PRODUCT_A_ID, viewedAt));
        return Boolean.TRUE.equals(transactionTemplate.execute(
            status -> activityRecordRepository.saveProductViewedIfAbsent(record)));
    }
}
