package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.ADDED_QTY;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.CART_ITEM_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.CART_QTY;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_120_OPTION_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ProductViewRecordIntegrationTest {

    private static final LocalDate VIEW_DAY = LocalDate.of(2026, 11, 10);
    private static final Long OTHER_PRODUCT_ID = PRODUCT_A_ID + 1;
    private static final int THREADS = 5;
    private static final long TIMEOUT_SECONDS = 60;

    @Autowired
    private TransactionTemplate transactionTemplate;

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

    @ParameterizedTest(name = "자녀 ID {0}")
    @MethodSource("childProfileIds")
    @DisplayName("PREF-15 같은 회원 · 자녀 · 상품이라도 날짜가 바뀌면(23:59:59와 다음 날 00:00:00) 상세 진입을 각각 저장한다")
    void saveViewsOfDifferentDays(Long childProfileId) {
        boolean lastOfDay = saveIfAbsent(childProfileId, VIEW_DAY.atTime(23, 59, 59));
        boolean firstOfNextDay = saveIfAbsent(childProfileId, VIEW_DAY.plusDays(1).atStartOfDay());

        assertThat(lastOfDay).isTrue();
        assertThat(firstOfNextDay).isTrue();
        assertThat(activityRecordRepository.findAll()).extracting(ActivityRecord::getOccurredAt)
            .containsExactlyInAnyOrder(VIEW_DAY.atTime(23, 59, 59), VIEW_DAY.plusDays(1).atStartOfDay());
    }

    @ParameterizedTest(name = "자녀 ID {0}")
    @MethodSource("childProfileIds")
    @DisplayName("PREF-15 같은 회원 · 자녀가 같은 날 다른 상품의 상세 진입을 저장하면 상품마다 각각 저장한다")
    void saveViewsOfDifferentProducts(Long childProfileId) {
        boolean first = saveIfAbsent(productViewed(childProfileId, PRODUCT_A_ID, VIEW_DAY.atTime(10, 0)));
        boolean second = saveIfAbsent(productViewed(childProfileId, OTHER_PRODUCT_ID, VIEW_DAY.atTime(10, 1)));

        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(activityRecordRepository.findAll()).extracting(ActivityRecord::getProductId)
            .containsExactlyInAnyOrder(PRODUCT_A_ID, OTHER_PRODUCT_ID);
    }

    @ParameterizedTest(name = "자녀 ID {0}")
    @MethodSource("childProfileIds")
    @DisplayName("PREF-15 같은 상세 진입을 실제 스레드 여러 개가 동시에 저장해도 예외 없이 한 줄만 남는다")
    void saveSameViewAtOnce(Long childProfileId) throws Exception {
        List<Boolean> saved = runAtOnce(i -> saveIfAbsent(childProfileId, VIEW_DAY.atTime(10, i)));

        assertThat(saved).containsOnlyOnce(true).hasSize(THREADS);
        assertThat(activityRecordRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("PREF-15 같은 회원 · 자녀 · 상품 · 날짜의 장바구니 담기 기록은 모두 저장하고, 상세 진입이 있어도 담기를 막지 않는다")
    void keepCartItemsAddedOfSameDay() {
        activityRecordRepository.saveAndFlush(cartItemAdded(VIEW_DAY.atTime(10, 0)));
        boolean viewed = saveIfAbsent(CHILD_1_ID, VIEW_DAY.atTime(10, 1));
        activityRecordRepository.saveAndFlush(cartItemAdded(VIEW_DAY.atTime(10, 2)));

        assertThat(viewed).isTrue();
        assertThat(activityRecordRepository.findAll()).extracting(ActivityRecord::getType)
            .containsExactlyInAnyOrder(
                ActivityType.CART_ITEM_ADDED, ActivityType.PRODUCT_VIEWED, ActivityType.CART_ITEM_ADDED);
    }

    @Test
    @DisplayName("상세 진입을 저장하면 넘긴 기록의 모든 칸이 그대로 남는다")
    void keepEveryFieldOfSavedView() {
        ActivityRecord record = productViewed(CHILD_1_ID, VIEW_DAY.atTime(10, 0));

        saveIfAbsent(record);

        assertThat(activityRecordRepository.findAll()).singleElement()
            .usingRecursiveComparison()
            .ignoringFields("id", "createdAt", "updatedAt")
            .isEqualTo(record);
    }

    @Test
    @DisplayName("같은 eventId로 다른 상품의 상세 진입을 저장하면 조용히 버리지 않고 예외가 난다")
    void failWhenEventIdIsAlreadyUsed() {
        UUID eventId = UUID.randomUUID();
        ActivityRecord first = viewedWithEventId(eventId, PRODUCT_A_ID);
        ActivityRecord other = viewedWithEventId(eventId, OTHER_PRODUCT_ID);
        saveIfAbsent(first);

        assertThatThrownBy(() -> saveIfAbsent(other)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(ActivityRecord::getProductId).isEqualTo(PRODUCT_A_ID);
    }

    // 저장소 프록시가 IllegalArgumentException을 Spring 예외로 바꾸므로 원인으로 확인한다
    @Test
    @DisplayName("상세 진입이 아닌 기록을 저장하려 하면 예외가 나고 아무것도 저장하지 않는다")
    void rejectRecordOtherThanProductView() {
        ActivityRecord cartItemAdded = cartItemAdded(VIEW_DAY.atTime(10, 0));

        assertThatThrownBy(() -> saveIfAbsent(cartItemAdded))
            .isInstanceOf(InvalidDataAccessApiUsageException.class)
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("상세 진입 기록만 저장할 수 있습니다");
        assertThat(activityRecordRepository.findAll()).isEmpty();
    }

    static Stream<Long> childProfileIds() {
        return Stream.of(CHILD_1_ID, null);
    }

    private boolean saveIfAbsent(Long childProfileId, LocalDateTime viewedAt) {
        return saveIfAbsent(productViewed(childProfileId, viewedAt));
    }

    // 저장소의 조건부 INSERT는 수정 쿼리라 트랜잭션 안에서 불러야 한다
    private boolean saveIfAbsent(ActivityRecord record) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
            status -> activityRecordRepository.saveProductViewedIfAbsent(record)));
    }

    private static ActivityRecord productViewed(Long childProfileId, LocalDateTime viewedAt) {
        return productViewed(childProfileId, PRODUCT_A_ID, viewedAt);
    }

    private static ActivityRecord productViewed(Long childProfileId, Long productId, LocalDateTime viewedAt) {
        return ActivityRecord.productViewed(new ActivityRecord.ProductViewedInfo(
            UUID.randomUUID(), MEMBER_1_ID, childProfileId, productId, viewedAt));
    }

    private static ActivityRecord viewedWithEventId(UUID eventId, Long productId) {
        return ActivityRecord.productViewed(new ActivityRecord.ProductViewedInfo(
            eventId, MEMBER_1_ID, CHILD_1_ID, productId, VIEW_DAY.atTime(10, 0)));
    }

    private static ActivityRecord cartItemAdded(LocalDateTime addedAt) {
        return ActivityRecord.cartItemAdded(new ActivityRecord.CartItemAddedInfo(UUID.randomUUID(), CART_ITEM_ID,
            MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, ADDED_QTY, CART_QTY, addedAt));
    }

    private <T> List<T> runAtOnce(IntFunction<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = IntStream.range(0, THREADS)
                .mapToObj(i -> executor.submit(() -> {
                    start.await();
                    return task.apply(i);
                }))
                .toList();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(get(future));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static <T> T get(Future<T> future) throws Exception {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new AssertionError("동시 저장이 예외로 끝났다", e.getCause());
        }
    }
}
