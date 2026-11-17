package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.QUICK_VIEWED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 담기 팝업을 연 것(팝업 조회)의 활동 기록을 실제 DB에 저장하는 규칙을 확인한다.
 */
@IntegrationTest
class ProductQuickViewRecordIntegrationTest {

    private static final int THREADS = 5;

    private static final long TIMEOUT_SECONDS = 10;

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

    @ParameterizedTest(name = "두 번째 팝업: {0}일 뒤, 상품 ID +{1}")
    @CsvSource({
        "1, 0",
        "0, 1",
    })
    @DisplayName("PREF-22 같은 회원이라도 날짜나 상품이 다르면 팝업 조회를 각각 기록한다")
    void keepQuickViewsOfOtherDayOrProduct(long daysLater, long productIdOffset) {
        saveIfAbsent(quickViewed(null, PRODUCT_A_ID, QUICK_VIEWED_AT.withHour(23).withMinute(59)));

        boolean saved = saveIfAbsent(quickViewed(null, PRODUCT_A_ID + productIdOffset,
            QUICK_VIEWED_AT.withHour(0).withMinute(0).plusDays(daysLater)));

        assertThat(saved).isTrue();
        assertThat(activityRecordRepository.findAll()).hasSize(2);
    }

    @ParameterizedTest(name = "먼저 연 자녀 {0}, 다음에 연 회원 {1} · 자녀 {2}")
    @CsvSource({
        "11, 1, 12",
        "11, 1,",
        ", 2,",
    })
    @DisplayName("PREF-22 같은 날 같은 상품이라도 회원이나 자녀가 다르면 팝업 조회를 각각 기록한다")
    void keepQuickViewsOfOtherMemberOrChild(Long firstChildId, Long secondMemberId, Long secondChildId) {
        saveIfAbsent(quickViewedBy(MEMBER_1_ID, firstChildId));

        boolean saved = saveIfAbsent(quickViewedBy(secondMemberId, secondChildId));

        assertThat(saved).isTrue();
        assertThat(activityRecordRepository.findAll()).hasSize(2);
    }

    @ParameterizedTest(name = "자녀 ID {0}")
    @MethodSource("childProfileIds")
    @DisplayName("PREF-22 같은 팝업 조회를 실제 스레드 여러 개가 동시에 저장해도 예외 없이 한 줄만 남는다")
    void saveSameQuickViewAtOnce(Long childProfileId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Boolean> saved = new ArrayList<>();
        try {
            List<Future<Boolean>> futures = IntStream.range(0, THREADS)
                .mapToObj(i -> executor.submit(() -> {
                    start.await();
                    return saveIfAbsent(quickViewed(childProfileId, PRODUCT_A_ID, QUICK_VIEWED_AT.plusMinutes(i)));
                }))
                .toList();
            start.countDown();
            for (Future<Boolean> future : futures) {
                saved.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(saved).containsOnlyOnce(true).hasSize(THREADS);
        assertThat(activityRecordRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("PREF-22 같은 회원 · 상품 · 날짜라도 상세 진입과 팝업 조회는 종류가 달라 둘 다 저장한다")
    void keepProductViewAndQuickViewOfSameDay() {
        transactionTemplate.execute(status -> activityRecordRepository.saveProductViewedIfAbsent(
            ActivityRecord.productViewed(new ActivityRecord.ProductViewedInfo(
                UUID.randomUUID(), MEMBER_1_ID, null, PRODUCT_A_ID, QUICK_VIEWED_AT))));

        boolean saved = saveIfAbsent(quickViewed(null, PRODUCT_A_ID, QUICK_VIEWED_AT));

        assertThat(saved).isTrue();
        assertThat(activityRecordRepository.findAll()).extracting(ActivityRecord::getType)
            .containsExactlyInAnyOrder(ActivityType.PRODUCT_VIEWED, ActivityType.PRODUCT_QUICK_VIEWED);
    }

    @Test
    @DisplayName("같은 eventId로 다른 상품의 팝업 조회를 저장하면 같은 날 한 번 규칙과 달리 조용히 버리지 않고 예외가 난다")
    void failWhenEventIdIsAlreadyUsed() {
        UUID eventId = UUID.randomUUID();
        saveIfAbsent(ActivityRecord.productQuickViewed(new ActivityRecord.ProductViewedInfo(
            eventId, MEMBER_1_ID, null, PRODUCT_A_ID, QUICK_VIEWED_AT)));

        assertThatThrownBy(() -> saveIfAbsent(ActivityRecord.productQuickViewed(
            new ActivityRecord.ProductViewedInfo(eventId, MEMBER_1_ID, null, PRODUCT_A_ID + 1, QUICK_VIEWED_AT))))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(ActivityRecord::getProductId).isEqualTo(PRODUCT_A_ID);
    }

    // 저장소 프록시가 IllegalArgumentException을 Spring 예외로 바꾸므로 원인으로 확인한다
    @Test
    @DisplayName("팝업 조회가 아닌 기록을 팝업 조회로 저장하려 하면 예외가 나고 아무것도 저장하지 않는다")
    void rejectRecordOtherThanQuickView() {
        ActivityRecord viewed = ActivityRecord.productViewed(new ActivityRecord.ProductViewedInfo(
            UUID.randomUUID(), MEMBER_1_ID, null, PRODUCT_A_ID, QUICK_VIEWED_AT));

        assertThatThrownBy(() -> saveIfAbsent(viewed))
            .isInstanceOf(InvalidDataAccessApiUsageException.class)
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("팝업 조회 기록만 저장할 수 있습니다");
        assertThat(activityRecordRepository.findAll()).isEmpty();
    }

    // 저장소의 조건부 INSERT는 수정 쿼리라 트랜잭션 안에서 불러야 한다
    private boolean saveIfAbsent(ActivityRecord record) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
            status -> activityRecordRepository.saveProductQuickViewedIfAbsent(record)));
    }

    private static ActivityRecord quickViewedBy(Long memberId, Long childProfileId) {
        return ActivityRecord.productQuickViewed(new ActivityRecord.ProductViewedInfo(
            UUID.randomUUID(), memberId, childProfileId, PRODUCT_A_ID, QUICK_VIEWED_AT));
    }

    private static ActivityRecord quickViewed(Long childProfileId, Long productId, LocalDateTime occurredAt) {
        return ActivityRecord.productQuickViewed(new ActivityRecord.ProductViewedInfo(
            UUID.randomUUID(), MEMBER_1_ID, childProfileId, productId, occurredAt));
    }
}
