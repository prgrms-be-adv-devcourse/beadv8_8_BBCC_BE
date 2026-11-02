package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 복사본 반영을 운영과 같은 길로 확인한다. 테스트 설정은 Kafka 수신 스위치가 꺼져 있어(kidly.community.kafka.consumer.enabled=false)
 * Spring 이벤트로 받는다: 한 트랜잭션에서 발행하고 커밋된 뒤 받는 쪽이 새 트랜잭션에서 처리한다. 결과는 DB에서 다시 읽는다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ProductReplicaIntegrationTest {

    private static final long PRODUCT_ID = 7L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.now().minusHours(1);
    // 받는 쪽이 저장하려던 상품 이름. 막힌 저장의 값이 로그에 나오지 않는지 이것으로 본다
    private static final String NAME_MARKER = "상품_" + UUID.randomUUID();
    private static final String OTHER_NAME = "먼저 저장된 상품";
    private static final Pattern ERROR_LINE = Pattern.compile("\\sERROR\\s+\\d+\\s+---");

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("스위치가 꺼져 있으면 Spring 이벤트로 받아 복사본을 저장한다(표시 상태 포함)")
    void springEventStored() {
        deliver(changed("상품 A", ProductStatus.ON_SALE, true, OCCURRED_AT));

        assertThat(rows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("name", "상품 A").containsEntry("display_status", "SOLD_OUT");
            assertThat(row).containsEntry("thumbnail_url", "https://img/7.png");
        });
    }

    @Test
    @DisplayName("같은 이벤트를 두 번 받아도 행 1개 · 값 1회분이고, 두 번째는 수정 시각도 바꾸지 않는다")
    void duplicateAppliedOnce() {
        ProductChangedEvent event = changed("상품 A", ProductStatus.ON_SALE, false, OCCURRED_AT);

        deliver(event);
        List<Map<String, Object>> first = rows();
        deliver(event);

        assertThat(rows()).hasSize(1).isEqualTo(first);
    }

    @Test
    @DisplayName("늦게 발생한 이벤트는 덮어쓰고, 그 뒤 옛 이벤트가 뒤늦게 와도 값을 유지한다")
    void olderEventAfterNewerKeepsValue() {
        deliver(changed("상품 A", ProductStatus.ON_SALE, false, OCCURRED_AT));
        deliver(changed("상품 B", ProductStatus.STOPPED, false, OCCURRED_AT.plusMinutes(1)));
        List<Map<String, Object>> newer = rows();

        deliver(changed("상품 C", ProductStatus.ON_SALE, false, OCCURRED_AT.plusSeconds(30)));

        assertThat(rows()).isEqualTo(newer).singleElement().satisfies(row ->
            assertThat(row).containsEntry("name", "상품 B").containsEntry("display_status", "SALE_ENDED"));
    }

    @Test
    @DisplayName("Spring 경로 동시 수신(처음 보는 같은 상품): 먼저 저장된 값보다 늦은 이벤트면 덮어쓰고, 예외 · ERROR 로그 없이 행은 1개다")
    void concurrentFirstInsertAppliesNewer(CapturedOutput output) throws Exception {
        whileOtherHoldsSameProduct(OCCURRED_AT, () -> CompletableFuture.runAsync(
            () -> deliver(changed(NAME_MARKER, ProductStatus.STOPPED, false, OCCURRED_AT.plusMinutes(1)))))
            .get(10, TimeUnit.SECONDS);

        assertThat(rows()).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("name", NAME_MARKER).containsEntry("display_status", "SALE_ENDED"));
        assertNoErrorLog(output);
    }

    @Test
    @DisplayName("Spring 경로 동시 수신(처음 보는 같은 상품): 먼저 저장된 값이 더 늦으면 그 값을 유지하고, 예외 · ERROR 로그 · 값 로그가 없다")
    void concurrentFirstInsertKeepsNewerStored(CapturedOutput output) throws Exception {
        whileOtherHoldsSameProduct(OCCURRED_AT.plusMinutes(1), () -> CompletableFuture.runAsync(
            () -> deliver(changed(NAME_MARKER, ProductStatus.STOPPED, false, OCCURRED_AT))))
            .get(10, TimeUnit.SECONDS);

        assertThat(rows()).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("name", OTHER_NAME).containsEntry("display_status", "ON_SALE"));
        assertNoErrorLog(output);
        assertThat(output.getOut()).doesNotContain(NAME_MARKER);
    }

    @Test
    @DisplayName("더 늦은 값이 이미 커밋된 상품에 이른 이벤트가 오면 값을 유지하고, 예외 · ERROR 로그가 없다")
    void olderEventOverCommittedNewerKeepsValue(CapturedOutput output) {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (?, ?, null, 'ON_SALE', ?, now(), now())",
            PRODUCT_ID, OTHER_NAME, OCCURRED_AT.plusMinutes(1));
        List<Map<String, Object>> committed = rows();

        deliver(changed(NAME_MARKER, ProductStatus.STOPPED, false, OCCURRED_AT));

        assertThat(rows()).isEqualTo(committed);
        assertNoErrorLog(output);
    }

    /**
     * 다른 트랜잭션이 같은 상품 행(occurredAt = otherOccurredAt)을 넣고 커밋을 미룬 채로 받기를 시작하고, 받는 쪽 저장이 그 행의
     * 잠금을 기다리는 것을 확인한 뒤 커밋한다. 실패해도 상대 트랜잭션을 놓아 준다.
     */
    private CompletableFuture<Void> whileOtherHoldsSameProduct(LocalDateTime otherOccurredAt,
        Supplier<CompletableFuture<Void>> receive) throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> other = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(s -> {
            jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, "
                + "display_status, source_occurred_at, created_at, updated_at) "
                + "values (?, ?, null, 'ON_SALE', ?, now(), now())", PRODUCT_ID, OTHER_NAME, otherOccurredAt);
            inserted.countDown();
            await(release);
        }));
        try {
            assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> receiving = receive.get();
            AsyncWait.until(Duration.ofSeconds(5), this::insertIsWaitingForLock);
            return receiving;
        } finally {
            release.countDown();
            other.get(10, TimeUnit.SECONDS);
        }
    }

    private boolean insertIsWaitingForLock() {
        Integer waiting = jdbcTemplate.queryForObject(
            "select count(*) from pg_stat_activity where wait_event_type = 'Lock' "
                + "and query like 'insert into community.replica_product%'", Integer.class);
        return waiting != null && waiting > 0;
    }

    private static void assertNoErrorLog(CapturedOutput output) {
        assertThat(output.getOut().lines().filter(line -> ERROR_LINE.matcher(line).find())).isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void deliver(ProductChangedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private List<Map<String, Object>> rows() {
        return jdbcTemplate.queryForList("select product_id, name, thumbnail_url, display_status, source_occurred_at, "
            + "created_at, updated_at from community.replica_product where product_id = ?", PRODUCT_ID);
    }

    private static ProductChangedEvent changed(String name, ProductStatus status, boolean soldOut,
        LocalDateTime occurredAt) {
        return new ProductChangedEvent(UUID.randomUUID(), occurredAt, new ProductChangedEvent.Data(PRODUCT_ID, 2L, name,
            3L, status, "https://img/7.png", occurredAt.minusDays(1), false, List.of(
                new ProductChangedEvent.Option(70L, "색상: 네이비 / 사이즈: 110", "110", 25_000L, null, null, soldOut,
                    OptionSaleStatus.ON_SALE))));
    }
}
