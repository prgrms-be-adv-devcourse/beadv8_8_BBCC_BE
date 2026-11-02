package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder.PurchaseConfirmed;
import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
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
 * 작성 자격 저장을 운영과 같은 길로 확인한다. 테스트 설정은 Kafka 수신 스위치가 꺼져 있어 Spring 이벤트로 받는다.
 * 동시 수신은 테스트가 다른 트랜잭션에서 같은 주문상품 행을 넣고 커밋을 미뤄 순서를 만든다: 수신 쪽 조회에는 아직 안 보이고,
 * 저장은 그 행의 커밋을 기다렸다가 유일 제약에 막힌다. Spring 경로(발행 트랜잭션 커밋 뒤 받는 쪽 새 트랜잭션)와
 * Kafka 경로(받는 쪽 트랜잭션 없음)를 따로 본다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ReviewEligibilityIntegrationTest {

    private static final long ORDER_ITEM_ID = 11L;
    // 나노초가 있는 고정 시각. DB(마이크로초, 반올림)에 저장하면 .123456으로 내려가 저장 값보다 "늦은" 값으로 보일 수 있는 경우다
    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_123);
    // 받는 쪽이 저장하려던 값. 막힌 저장의 값이 로그에 나오지 않는지 이것으로 본다
    private static final String SIZE_MARKER = "SIZE_" + UUID.randomUUID().toString().replace("-", "");
    private static final String OTHER_SIZE = "110";
    private static final Pattern ERROR_LINE = Pattern.compile("\\sERROR\\s+\\d+\\s+---");

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private ReviewEligibilityRecorder recorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Spring 이벤트로 받은 구매확정은 자격 1건으로 저장된다")
    void stored() {
        deliver(event());

        assertThat(rows()).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("order_id", 12L).containsEntry("member_id", 13L).containsEntry("product_id", 14L)
            .containsEntry("option_id", 15L).containsEntry("size", SIZE_MARKER));
    }

    @Test
    @DisplayName("같은 구매확정을 두 번 받아도 행 1개이고 값 · 생성 · 수정 시각이 그대로다")
    void duplicateAppliedOnce() {
        OrderItemPurchaseConfirmedEvent event = event();

        deliver(event);
        List<Map<String, Object>> first = rows();
        deliver(event);

        assertThat(rows()).hasSize(1).isEqualTo(first);
    }

    @Test
    @DisplayName("Spring 경로 동시 수신: 발행 트랜잭션 커밋 뒤 받는 쪽 저장이 유일 제약에 막혀도 예외 · ERROR 로그 없이 끝나고 행은 1개다")
    void concurrentSpringPathConverges(CapturedOutput output) throws Exception {
        OrderItemPurchaseConfirmedEvent event = event();

        CompletableFuture<Void> receiving = whileOtherHoldsSameRow(
            () -> CompletableFuture.runAsync(() -> deliver(event)));

        receiving.get(10, TimeUnit.SECONDS);
        assertThat(rows()).singleElement().satisfies(row -> assertThat(row).containsEntry("size", OTHER_SIZE));
        assertThat(output.getOut().lines().filter(line -> ERROR_LINE.matcher(line).find())).isEmpty();
        assertThat(output.getOut()).doesNotContain(SIZE_MARKER);
    }

    @Test
    @DisplayName("Kafka 경로 동시 수신(받는 쪽 트랜잭션 없음): 저장이 유일 제약에 막혀도 예외 없이 끝나고 행은 1개다")
    void concurrentDirectConverges() throws Exception {
        PurchaseConfirmed confirmed =
            new PurchaseConfirmed(ORDER_ITEM_ID, 12L, 13L, 14L, 15L, SIZE_MARKER, CONFIRMED_AT);

        CompletableFuture<Void> receiving = whileOtherHoldsSameRow(
            () -> CompletableFuture.runAsync(() -> recorder.record(confirmed)));

        receiving.get(10, TimeUnit.SECONDS);
        assertThat(rows()).hasSize(1);
    }

    /**
     * 다른 트랜잭션이 같은 주문상품 행을 넣고 커밋을 미룬 채로 받기를 시작하고, 받는 쪽 저장이 그 행의 잠금을 기다리는 것을 확인한 뒤
     * 커밋한다(받는 쪽 조회에는 아직 안 보이고, 저장은 커밋 뒤 유일 제약에 막힌다). 실패해도 상대 트랜잭션을 놓아 준다.
     */
    private CompletableFuture<Void> whileOtherHoldsSameRow(Supplier<CompletableFuture<Void>> receive)
        throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> other = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(s -> {
            jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, "
                + "product_id, option_id, size, confirmed_at, created_at, updated_at) "
                + "values (?, 12, 13, 14, 15, ?, ?, now(), now())", ORDER_ITEM_ID, OTHER_SIZE, CONFIRMED_AT);
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
                + "and query like 'insert into community.review_eligibility%'", Integer.class);
        return waiting != null && waiting > 0;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void deliver(OrderItemPurchaseConfirmedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private List<Map<String, Object>> rows() {
        return jdbcTemplate.queryForList("select order_item_id, order_id, member_id, product_id, option_id, size, "
            + "confirmed_at, created_at, updated_at from community.review_eligibility where order_item_id = ?",
            ORDER_ITEM_ID);
    }

    private static OrderItemPurchaseConfirmedEvent event() {
        return new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), CONFIRMED_AT.plusSeconds(1),
            new OrderItemPurchaseConfirmedEvent.Data(ORDER_ITEM_ID, 12L, 13L, 20L, null, 14L, 15L, SIZE_MARKER, 1,
                25_000L, CONFIRMED_AT));
    }
}
