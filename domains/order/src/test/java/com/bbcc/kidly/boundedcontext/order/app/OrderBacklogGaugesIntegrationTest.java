package com.bbcc.kidly.boundedcontext.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.order.app.config.StuckOrderThresholds;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.OrderDeadLetterService;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import com.bbcc.kidly.boundedcontext.order.out.outbox.OrderOutboxMessage;
import com.bbcc.kidly.boundedcontext.order.out.persistence.JpaOrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 주문 운영 적체 게이지(#1067)가 실제 DB 조회로 값을 만드는지 본다. 처리된 것 · 다른 상태는 세지 않는다.
 */
@IntegrationTest
class OrderBacklogGaugesIntegrationTest {

    private static final Long BUYER_ID = 7_600L;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private OrderBacklogGauges gauges;

    @Autowired
    private JpaOrderOutboxRepository outboxRepository;

    @Autowired
    private OrderDeadLetterService deadLetterService;

    @Autowired
    private OrderEventFailureRepository failureRepository;

    @Autowired
    private PaymentRecoveryRepository recoveryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StuckOrderThresholds thresholds;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("아웃박스: 미전송(격리 제외) 수 · 가장 오래된 미전송 나이(초) · 격리 수. 보낸 것은 세지 않고, 없으면 나이 0")
    void outboxGauges() {
        assertThat(gauge("kidly.order.outbox.unsent.messages")).isZero();
        assertThat(gauge("kidly.order.outbox.oldest.unsent.age.seconds")).isZero();
        LocalDateTime now = LocalDateTime.now();
        save("1", now.minusMinutes(5));
        save("2", now.minusMinutes(1));
        Long sent = save("3", now.minusHours(1));
        Long quarantined = save("4", now.minusHours(2));
        jdbcTemplate.update("update orders.order_outbox set sent_at = ? where id = ?", now, sent);
        jdbcTemplate.update("update orders.order_outbox set quarantined_at = ? where id = ?", now, quarantined);

        assertThat(gauge("kidly.order.outbox.unsent.messages")).isEqualTo(2.0);
        assertThat(gauge("kidly.order.outbox.oldest.unsent.age.seconds")).isCloseTo(300.0, within(5.0));
        assertThat(gauge("kidly.order.outbox.quarantined.messages")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("DLT 미처리(OPEN) · 이벤트 실패 보관 미처리(PENDING) · 결제 복구 대기(PENDING)만 센다")
    void waitingRecords() {
        receiveDeadLetter(1L);
        receiveDeadLetter(2L);
        jdbcTemplate.update("update orders.order_dead_letter set status = 'CLOSED' where source_offset = 2");
        failureRepository.save(OrderEventFailure.open(OrderEventFailure.EventType.STOCK_DEDUCTED, 10L, List.of(),
            "DataIntegrityViolationException", LocalDateTime.now()));
        Long recovered = recoveryRepository.save(PaymentRecovery.open(11L, 500L, BUYER_ID, 25_000L, List.of(),
            "DataIntegrityViolationException", true, LocalDateTime.now())).getId();
        recoveryRepository.save(PaymentRecovery.open(12L, 501L, BUYER_ID, 25_000L, List.of(),
            "DataIntegrityViolationException", true, LocalDateTime.now()));
        jdbcTemplate.update("update orders.payment_recovery set status = 'RECOVERED' where id = ?", recovered);

        assertThat(registry.get("kidly.order.dlt.messages").tag("state", "open").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("kidly.order.event.failures").tag("state", "pending").gauge().value())
            .isEqualTo(1.0);
        assertThat(registry.get("kidly.order.payment.recoveries").tag("state", "pending").gauge().value())
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName("멈춘 주문 수는 관리자 요약과 같은 정의로 종류별(kind 태그)이고, 30초 캐시가 지나면 새 값을 읽는다")
    void stuckOrders() {
        gauges.expireStuckCache();
        assertThat(stuck("stock_check_stale")).isZero();

        Long orderId = placedOrder();
        jdbcTemplate.update("update orders.orders set created_at = ?, payment_requested_at = null where id = ?",
            LocalDateTime.now().minus(thresholds.stockWait()).minusMinutes(1), orderId);

        assertThat(stuck("stock_check_stale")).as("캐시 안에서는 그대로").isZero();
        gauges.expireStuckCache();
        assertThat(stuck("stock_check_stale")).isEqualTo(1.0);
        assertThat(stuck("payment_expiry_missed")).isZero();
        assertThat(registry.find("kidly.order.stuck.orders").gauges()).hasSize(7);
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }

    private double stuck(String kind) {
        return registry.get("kidly.order.stuck.orders").tag("kind", kind).gauge().value();
    }

    private Long save(String key, LocalDateTime createdAt) {
        Long id = outboxRepository.save(OrderOutboxMessage.create(UUID.randomUUID(), "order.created",
            "kidly.order.events", key, "{}")).getId();
        jdbcTemplate.update("update orders.order_outbox set created_at = ? where id = ?", createdAt, id);
        return id;
    }

    private void receiveDeadLetter(long offset) {
        deadLetterService.receive(new ReceiveDeadLetterCommand("kidly.product.events", 0, offset, null, null, null,
            "{broken", "com.bbcc.kidly.boundedcontext.order.in.event.UnreadableOrderMessageException", "깨진 본문",
            null));
    }

    private Long placedOrder() {
        LocalDateTime now = LocalDateTime.now();
        Order order = orderRepository.save(Order.place(BUYER_ID, List.of(new Order.RequestedItem(
                new OrderItem.ProductSnapshot(1L, 1L, 2L, "상품", null, null, 10_000L, 1L), true, null, 1, 10_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), now));
        // 결제 마감은 다른 종류(결제 만료 누락)에 걸리지 않게 먼 뒤로 둔다
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?", now.plusDays(1),
            order.getId());
        return order.getId();
    }
}
