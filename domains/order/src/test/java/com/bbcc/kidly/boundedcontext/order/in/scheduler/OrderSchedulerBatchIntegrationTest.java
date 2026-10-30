package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderScheduleIntervals;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderSchedulerProperties;
import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.PaymentExpiryGate;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 스케줄러 한 실행이 실제 DB에서 여러 배치를 이어 처리하는지. 작은 건수(batch 2)로 스케줄러를 직접 만들어
 * 같은 컨텍스트의 실제 서비스로 돌린다(설정을 바꾸려고 새 컨텍스트를 띄우지 않음).
 * 앞쪽 건의 계속되는 실패는 실제 서비스(트랜잭션 프록시)에 그대로 넘기는 mock에서 그 건만 예외를 던져 만든다.
 * 여러 인스턴스 동시 실행(#530)은 같은 설정의 스케줄러 두 개를 출발 신호 하나로 함께 돌린다. 반복마다 새 대상을 만들고
 * 결과 · 이벤트 · 로그는 그 회차 대상 ID로 걸러 센다(반복 사이에 DB를 비우지 않음).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OrderSchedulerBatchIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(OrderSchedulerBatchIntegrationTest.class);
    private static final int REPEAT = 10;
    private static final int TARGETS = 5;
    private static final Pattern ORDER_ID = Pattern.compile("orderId=(\\d+)");

    private static final OrderScheduleIntervals INTERVALS = new OrderScheduleIntervals(
        java.time.Duration.ofMinutes(1), new OrderScheduleIntervals.AutoConfirm(java.time.Duration.ofMinutes(10)));

    private static final Long BUYER_ID = 1L;

    // 결제 이벤트 Kafka 리스너가 없어 만료를 늘 허락한다(#561)
    @Autowired
    private PaymentExpiryGate paymentExpiryGate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Test
    @DisplayName("앞쪽 주문의 만료 취소가 계속 실패해도 같은 실행에서 뒤쪽 주문들은 여러 배치에 걸쳐 취소된다")
    void frontFailureDoesNotBlockTheRest() {
        List<Long> ids = overdueOrders(5);
        OrderService failingFirst = mock(OrderService.class, delegatesTo(orderService));
        doThrow(new IllegalStateException("계속 실패")).when(failingFirst).cancelForPaymentExpiry(ids.getFirst());

        new OrderScheduler(failingFirst, new OrderSchedulerProperties(2, 10), INTERVALS, paymentExpiryGate)
            .expireOverduePayments();

        assertThat(statusOf(ids.getFirst())).isEqualTo("PENDING_PAYMENT");
        for (Long id : ids.subList(1, 5)) {
            assertThat(statusOf(id)).isEqualTo("CANCELLED");
        }
    }

    @Test
    @DisplayName("max-per-run(3)에 닿으면 그 실행은 앞의 3건만 처리하고 멈추며, 나머지는 다음 실행에서 처리된다")
    void stopsAtMaxPerRunAndContinuesNextRun() {
        List<Long> ids = overdueOrders(5);
        OrderScheduler scheduler = new OrderScheduler(orderService, new OrderSchedulerProperties(2, 3), INTERVALS,
            paymentExpiryGate);

        scheduler.expireOverduePayments();

        assertThat(ids).extracting(this::statusOf)
            .containsExactly("CANCELLED", "CANCELLED", "CANCELLED", "PENDING_PAYMENT", "PENDING_PAYMENT");

        scheduler.expireOverduePayments();

        assertThat(ids).extracting(this::statusOf).containsOnly("CANCELLED");
    }

    @Test
    @DisplayName("자동 구매확정도 앞쪽 주문상품이 계속 실패해도 뒤쪽 주문상품을 같은 실행에서 여러 배치에 걸쳐 확정한다")
    void autoConfirmFrontFailureDoesNotBlockTheRest() {
        List<Long> orderIds = deliveredLongAgo(4);
        OrderService failingFirst = mock(OrderService.class, delegatesTo(orderService));
        Long firstItemId = itemIdOf(orderIds.getFirst());
        doThrow(new IllegalStateException("계속 실패")).when(failingFirst)
            .confirmAutomatically(orderIds.getFirst(), firstItemId);

        new OrderScheduler(failingFirst, new OrderSchedulerProperties(2, 10), INTERVALS, paymentExpiryGate)
            .autoConfirm();

        assertThat(itemStatusOf(orderIds.getFirst())).isEqualTo("ORDERED");
        for (Long orderId : orderIds.subList(1, 4)) {
            assertThat(itemStatusOf(orderId)).isEqualTo("CONFIRMED");
        }
    }

    @Test
    @DisplayName("자동 구매확정도 max-per-run(3)에 닿으면 주문상품 5건 중 앞의 3건만 확정하고, 다음 실행에서 나머지를 확정한다")
    void autoConfirmStopsAtMaxPerRun() {
        List<Long> orderIds = deliveredLongAgo(5);
        OrderScheduler scheduler = new OrderScheduler(orderService, new OrderSchedulerProperties(2, 3), INTERVALS,
            paymentExpiryGate);

        scheduler.autoConfirm();

        assertThat(orderIds).extracting(this::itemStatusOf)
            .containsExactly("CONFIRMED", "CONFIRMED", "CONFIRMED", "ORDERED", "ORDERED");

        scheduler.autoConfirm();

        assertThat(orderIds).extracting(this::itemStatusOf).containsOnly("CONFIRMED");
    }

    /**
     * 결제 마감이 1분 전에 지난 결제대기 주문 count건. 주문 ID 오름차순.
     */
    private List<Long> overdueOrders(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Long id = orderRepository.save(place()).getId();
            jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
                LocalDateTime.now().minusMinutes(1), id);
            ids.add(id);
        }
        return ids;
    }

    /**
     * 결제완료 · 자동 확정 기간보다 하루 더 전에 배송 완료된 주문상품 하나씩인 주문 count건.
     */
    private List<Long> deliveredLongAgo(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Long id = orderRepository.save(place()).getId();
            jdbcTemplate.update("update orders.orders set status = 'PAID', paid_at = ?, payment_id = ? where id = ?",
                LocalDateTime.now().minusDays(30), 500L + i, id);
            jdbcTemplate.update("update orders.order_item set delivered_at = ? where order_id = ?",
                LocalDateTime.now().minusDays(30), id);
            ids.add(id);
        }
        return ids;
    }

    @Test
    @DisplayName("결제 마감 만료 스케줄러 두 인스턴스가 같은 대상에 동시에 돌아도 주문마다 만료 취소 1번 · order.cancelled 1건 · "
        + "이력 1줄이고, 진 쪽은 WARN만 남긴 채(보관 없음) 실행을 끝까지 마친다 (#530)")
    void expireRunsConcurrentlyCancelOnce(CapturedOutput output) throws InterruptedException {
        // 테스트마다 주문 ID가 1부터 다시 시작해 다른 테스트의 같은 orderId 이벤트가 섞이지 않게 비운다
        recorder.clear();
        for (int round = 1; round <= REPEAT; round++) {
            List<Long> ids = overdueOrders(TARGETS);
            int mark = output.getAll().length();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                instance()::expireOverduePayments, instance()::expireOverduePayments));

            String logs = output.getAll().substring(mark);
            List<Long> lost = warnedOrderIds(logs, "결제 마감 만료 취소 실패: ");
            LOG.info("[#530] 결제 마감 만료 ×2 동시 {}회차: 진 쪽 WARN {}건", round, lost.size());
            assertThat(outcome.conflicts()).as("진 쪽 예외는 스케줄러 밖으로 나오지 않음(%d회차)", round).isZero();
            assertThat(outcome.unexpected()).as("스케줄러 밖으로 나온 예외(%d회차)", round).isEmpty();
            for (Long id : ids) {
                assertThat(statusOf(id)).isEqualTo("CANCELLED");
                assertThat(cancelReasonOf(id)).isEqualTo("PAYMENT_EXPIRED");
                assertThat(recorder.events(OrderCancelledEvent.class))
                    .as("order.cancelled(%d회차 · 주문 %d)", round, id)
                    .filteredOn(event -> event.data().orderId().equals(id)).hasSize(1);
                assertThat(historyRows("ORDER", id)).as("상태 이력(%d회차 · 주문 %d)", round, id)
                    .containsExactly("PENDING_PAYMENT>CANCELLED PAYMENT_EXPIRED scheduler:payment-expiry");
                assertThat(eventFailureCount(id)).as("스케줄러 실패는 보관하지 않음(%d회차)", round).isZero();
            }
            assertThat(lost).as("진 쪽 WARN은 이번 회차 대상만(%d회차)", round).isSubsetOf(ids);
            assertThat(orderErrorLines(logs)).as("ERROR 없음(알려진 상품 쪽 한 종류 제외, %d회차)", round).isEmpty();
        }
    }

    @Test
    @DisplayName("자동 구매확정 스케줄러 두 인스턴스가 같은 대상에 동시에 돌아도 주문상품마다 AUTO 확정 1번 · "
        + "order-item.purchase-confirmed 1건 · 이력 1줄이고, 진 쪽은 WARN만 남긴 채(보관 없음) 실행을 끝까지 마친다 (#530)")
    void autoConfirmRunsConcurrentlyConfirmOnce(CapturedOutput output) throws InterruptedException {
        recorder.clear();
        for (int round = 1; round <= REPEAT; round++) {
            List<Long> orderIds = deliveredLongAgo(TARGETS);
            int mark = output.getAll().length();

            ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(
                instance()::autoConfirm, instance()::autoConfirm));

            String logs = output.getAll().substring(mark);
            List<Long> lost = warnedOrderIds(logs, "자동 구매확정 실패: ");
            LOG.info("[#530] 자동 구매확정 ×2 동시 {}회차: 진 쪽 WARN {}건", round, lost.size());
            assertThat(outcome.conflicts()).as("진 쪽 예외는 스케줄러 밖으로 나오지 않음(%d회차)", round).isZero();
            assertThat(outcome.unexpected()).as("스케줄러 밖으로 나온 예외(%d회차)", round).isEmpty();
            for (Long orderId : orderIds) {
                Long itemId = itemIdOf(orderId);
                assertThat(itemStatusOf(orderId)).isEqualTo("CONFIRMED");
                assertThat(confirmTypeOf(orderId)).isEqualTo("AUTO");
                assertThat(recorder.events(OrderItemPurchaseConfirmedEvent.class))
                    .as("purchase-confirmed(%d회차 · 주문상품 %d)", round, itemId)
                    .filteredOn(event -> event.data().orderItemId().equals(itemId)).hasSize(1);
                assertThat(historyRows("ORDER_ITEM", itemId)).as("상태 이력(%d회차 · 주문상품 %d)", round, itemId)
                    .containsExactly("ORDERED>CONFIRMED AUTO scheduler:auto-confirm");
                assertThat(eventFailureCount(orderId)).as("스케줄러 실패는 보관하지 않음(%d회차)", round).isZero();
            }
            assertThat(lost).as("진 쪽 WARN은 이번 회차 대상만(%d회차)", round).isSubsetOf(orderIds);
            assertThat(orderErrorLines(logs)).as("ERROR 없음(알려진 상품 쪽 한 종류 제외, %d회차)", round).isEmpty();
        }
    }

    private OrderScheduler instance() {
        return new OrderScheduler(orderService, new OrderSchedulerProperties(2, 10), INTERVALS, paymentExpiryGate);
    }

    // 진 쪽이 남긴 건별 실패 WARN 줄의 주문 ID(한 줄 = 한 건)
    private static List<Long> warnedOrderIds(String logs, String message) {
        return logs.lines()
            .filter(line -> line.contains(" WARN ") && line.contains(message))
            .map(line -> {
                Matcher matcher = ORDER_ID.matcher(line);
                return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
            })
            .toList();
    }

    // ERROR 줄 전부에서 알려진 한 종류만 뺀다: order.cancelled를 받은 상품 쪽이 이 테스트가 상품에 만들지 않은 옵션(1)이라
    // 남기는 "없는 옵션이 든 주문 취소됨" ERROR. 주문 결과와 무관하다
    private static List<String> orderErrorLines(String logs) {
        return logs.lines()
            .filter(line -> line.contains(" ERROR "))
            .filter(line -> !(line.contains("InventoryRestorationService") && line.contains("없는 옵션이 든 주문")))
            .toList();
    }

    // 대상의 상태 이력을 "이전>다음 사유 출처"로
    private List<String> historyRows(String targetType, Long targetId) {
        return jdbcTemplate.queryForList("select from_status || '>' || to_status || ' ' || reason || ' ' || source "
            + "from orders.order_status_history where target_type = ? and target_id = ? order by id",
            String.class, targetType, targetId);
    }

    private long eventFailureCount(Long orderId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from orders.order_event_failure where order_id = ?", Long.class, orderId);
    }

    private String cancelReasonOf(Long orderId) {
        return jdbcTemplate.queryForObject(
            "select cancel_reason from orders.orders where id = ?", String.class, orderId);
    }

    private String confirmTypeOf(Long orderId) {
        return jdbcTemplate.queryForObject(
            "select confirm_type from orders.order_item where order_id = ?", String.class, orderId);
    }

    private String statusOf(Long orderId) {
        return jdbcTemplate.queryForObject("select status from orders.orders where id = ?", String.class, orderId);
    }

    private Long itemIdOf(Long orderId) {
        return jdbcTemplate.queryForObject("select id from orders.order_item where order_id = ?", Long.class, orderId);
    }

    private String itemStatusOf(Long orderId) {
        return jdbcTemplate.queryForObject(
            "select status from orders.order_item where order_id = ?", String.class, orderId);
    }

    private static Order place() {
        return Order.place(BUYER_ID, List.of(new Order.RequestedItem(
                new OrderItem.ProductSnapshot(1L, 1L, 2L, "상품", null, "110", 10_000L, 1L), true, null, 1, 10_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now());
    }
}
