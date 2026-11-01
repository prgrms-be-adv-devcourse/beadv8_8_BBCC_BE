package com.bbcc.kidly.boundedcontext.order.app.stuck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.stuck.dto.StuckOrderResult;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 관리자 멈춘 주문 조회(#445, 실제 PostgreSQL). 아웃박스 멈춤(OUTBOX_STUCK, #602)은
 * OutboxStuckIntegrationTest가 본다. 지금(now)을 고정하고 저장된 시각을 jdbc로 맞춘다.
 * 기준 시각(since) = 저장된 시각 + 기준 시간, since가 now보다 이전이면 나오고 정확히 같거나 뒤면 나오지 않는다.
 * 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class StuckOrderIntegrationTest {

    private static final Long BUYER_ID = 7_500L;
    private static final Long SELLER_ID = 2L;

    @Autowired
    private StuckOrderService stuckOrderService;

    @Autowired
    private StuckOrderThresholds thresholds;

    @Autowired
    private Order.Policy orderPolicy;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private PaymentRecoveryRepository recoveryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now().withNano(0);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = StuckOrderKind.class, names = "OUTBOX_STUCK", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("기준 시각이 지금 1초 전이면 나오고, 정확히 지금 · 1초 뒤면 나오지 않는다(건수 = 목록 전체 건수)")
    void boundary(StuckOrderKind kind) {
        Long before = create(kind, now.minusSeconds(1));
        create(kind, now);
        create(kind, now.plusSeconds(1));

        Page<StuckOrderResult> page = stuckOrderService.getStuckOrders(kind.name(), now, 0, 20);

        assertThat(page.getContent()).singleElement().satisfies(result -> {
            assertThat(result.kind()).isEqualTo(kind.name());
            assertThat(result.targetId()).isEqualTo(before);
            assertThat(result.targetType()).isEqualTo(kind.targetType());
            assertThat(result.status()).isEqualTo(kind.status());
            assertThat(result.since()).isEqualTo(now.minusSeconds(1));
        });
        assertThat(stuckOrderService.getSummary(now)).containsEntry(kind, 1L);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("건수 요약은 여섯 종류 키를 선언 순서로 모두 담고 0도 남긴다")
    void summaryKeepsZeroKeys() {
        create(StuckOrderKind.SHIPPING_DELAYED, now.minusSeconds(1));

        Map<StuckOrderKind, Long> summary = stuckOrderService.getSummary(now);

        assertThat(summary.keySet()).containsExactly(StuckOrderKind.values());
        assertThat(summary).containsEntry(StuckOrderKind.SHIPPING_DELAYED, 1L)
            .containsEntry(StuckOrderKind.STOCK_CHECK_STALE, 0L)
            .containsEntry(StuckOrderKind.AUTO_CONFIRM_MISSED, 0L);
    }

    @Test
    @DisplayName("오래된 것부터: 기준 시각 오름차순, 같은 시각이면 대상 ID 오름차순(페이지 경계 고정)")
    void sortedOldestFirst() {
        Long newer = create(StuckOrderKind.SHIPPING_DELAYED, now.minusSeconds(10));
        Long sameA = create(StuckOrderKind.SHIPPING_DELAYED, now.minusSeconds(20));
        Long sameB = create(StuckOrderKind.SHIPPING_DELAYED, now.minusSeconds(20));
        Long oldest = create(StuckOrderKind.SHIPPING_DELAYED, now.minusSeconds(30));

        List<Long> first = targetIds(stuckOrderService.getStuckOrders("SHIPPING_DELAYED", now, 0, 2));
        Page<StuckOrderResult> second = stuckOrderService.getStuckOrders("SHIPPING_DELAYED", now, 1, 2);

        assertThat(first).containsExactly(oldest, sameA);
        assertThat(targetIds(second)).containsExactly(sameB, newer);
        assertThat(second.getTotalElements()).isEqualTo(4);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("결제 마감 지남: 결제 반영 실패 기록이 PENDING이면 빠지고(일부러 만료를 건너뜀), NOT_RETRYABLE이면 나온다")
    void expiryExcludesPendingRecovery() {
        Long pending = create(StuckOrderKind.PAYMENT_EXPIRY_MISSED, now.minusSeconds(1));
        Long notRetryable = create(StuckOrderKind.PAYMENT_EXPIRY_MISSED, now.minusSeconds(1));
        recoveryRepository.save(recoveryOf(pending, true));
        recoveryRepository.save(recoveryOf(notRetryable, false));

        assertThat(targetIds(stuckOrderService.getStuckOrders("PAYMENT_EXPIRY_MISSED", now, 0, 20)))
            .containsExactly(notRetryable);
    }

    @Test
    @DisplayName("한 주문이 여러 종류에 함께 나올 수 있다: 결제 요청 전에 마감까지 지나면 재고 · 만료 두 종류에 나온다")
    void sameOrderInTwoKinds() {
        Long orderId = create(StuckOrderKind.STOCK_CHECK_STALE, now.minusSeconds(1));
        jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
            now.minus(thresholds.expiryGrace()).minusSeconds(1), orderId);

        Map<StuckOrderKind, Long> summary = stuckOrderService.getSummary(now);

        assertThat(summary).containsEntry(StuckOrderKind.STOCK_CHECK_STALE, 1L)
            .containsEntry(StuckOrderKind.PAYMENT_EXPIRY_MISSED, 1L);
        assertThat(summary.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 상태는 나오지 않는다: 결제 요청함 · 결제완료(배송 있음) · 처리된 복구 기록 · 발송된 배송 · 확정된 주문상품")
    void otherStatesExcluded() {
        Long requested = create(StuckOrderKind.STOCK_CHECK_STALE, now.minusSeconds(1));
        jdbcTemplate.update("update orders.orders set payment_requested_at = ? where id = ?", now, requested);
        Long withDelivery = create(StuckOrderKind.PAID_WITHOUT_DELIVERY, now.minusSeconds(1));
        deliveryRepository.save(Delivery.create(withDelivery, SELLER_ID));
        Long recovered = create(StuckOrderKind.RECOVERY_PENDING_STALE, now.minusSeconds(1));
        jdbcTemplate.update("update orders.payment_recovery set status = 'RECOVERED' where id = ?", recovered);
        Long shipped = create(StuckOrderKind.SHIPPING_DELAYED, now.minusSeconds(1));
        jdbcTemplate.update("update orders.delivery set status = 'SHIPPING' where id = ?", shipped);
        Long confirmed = create(StuckOrderKind.AUTO_CONFIRM_MISSED, now.minusSeconds(1));
        jdbcTemplate.update("update orders.order_item set status = 'CONFIRMED' where id = ?", confirmed);

        assertThat(stuckOrderService.getSummary(now).values()).containsOnly(0L);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = StuckOrderKind.class, names = "OUTBOX_STUCK", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("취소된 주문은 어느 종류에도 나오지 않는다")
    void cancelledOrderExcluded(StuckOrderKind kind) {
        Long targetId = create(kind, now.minusSeconds(1));
        Long orderId = stuckOrderService.getStuckOrders(kind.name(), now, 0, 20).getContent().getFirst().orderId();

        cancel(orderId);

        assertThat(stuckOrderService.getSummary(now)).containsEntry(kind, 0L);
        assertThat(stuckOrderService.getStuckOrders(kind.name(), now, 0, 20).getContent())
            .extracting(StuckOrderResult::targetId).doesNotContain(targetId);
    }

    @Test
    @DisplayName("조회는 상태 · version · 상태 이력을 바꾸지 않는다(읽기 전용)")
    void readOnly() {
        orderKinds().forEach(kind -> create(kind, now.minusSeconds(1)));
        List<Map<String, Object>> before = snapshot();

        stuckOrderService.getSummary(now);
        Arrays.stream(StuckOrderKind.values())
            .forEach(kind -> stuckOrderService.getStuckOrders(kind.name(), now, 0, 20));
        // 아웃박스 멈춤 조회도 읽기만 한다
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("모르는 종류 · 소문자 · 빈 값 · 없음(null)이면 INVALID_STUCK_KIND")
    void unknownKind() {
        for (String kind : Arrays.asList("UNKNOWN", "shipping_delayed", "", null)) {
            assertThatThrownBy(() -> stuckOrderService.getStuckOrders(kind, now, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_STUCK_KIND));
        }
    }

    @Test
    @DisplayName("offset이 int를 넘는 큰 page는 조회 없이 빈 목록, 전체 건수는 실제 값")
    void hugePageIsEmpty() {
        create(StuckOrderKind.SHIPPING_DELAYED, now.minusSeconds(1));

        Page<StuckOrderResult> page = stuckOrderService.getStuckOrders("SHIPPING_DELAYED", now, 1_073_741_824, 2);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    /**
     * 그 종류의 대상 하나를 기준 시각이 since가 되게 만든다(저장된 시각 = since − 기준 시간).
     *
     * @return 대상 ID(주문 · 결제 복구 기록 · 배송 · 주문상품)
     */
    private Long create(StuckOrderKind kind, LocalDateTime since) {
        Order order = orderRepository.save(Order.place(BUYER_ID, List.of(new Order.RequestedItem(
                new OrderItem.ProductSnapshot(1L, 1L, SELLER_ID, "상품", null, null, 10_000L, 1L), true, null, 1,
                10_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), now));
        Long orderId = order.getId();
        Long itemId = order.getItems().getFirst().getId();
        // 결제 마감 · 주문 시각은 다른 종류에 걸리지 않게 먼 뒤로 둔다
        jdbcTemplate.update("update orders.orders set created_at = ?, payment_due_at = ?, payment_requested_at = ? "
            + "where id = ?", now, now.plusDays(1), now, orderId);
        return switch (kind) {
            case STOCK_CHECK_STALE -> {
                jdbcTemplate.update("update orders.orders set created_at = ?, payment_requested_at = null "
                    + "where id = ?", since.minus(thresholds.stockWait()), orderId);
                yield orderId;
            }
            case PAYMENT_EXPIRY_MISSED -> {
                jdbcTemplate.update("update orders.orders set payment_due_at = ? where id = ?",
                    since.minus(thresholds.expiryGrace()), orderId);
                yield orderId;
            }
            case PAID_WITHOUT_DELIVERY -> {
                jdbcTemplate.update("update orders.orders set status = 'PAID', paid_at = ? where id = ?",
                    since.minus(thresholds.deliveryWait()), orderId);
                yield orderId;
            }
            case RECOVERY_PENDING_STALE -> {
                Long recoveryId = recoveryRepository.save(recoveryOf(orderId, true)).getId();
                jdbcTemplate.update("update orders.payment_recovery set created_at = ? where id = ?",
                    since.minus(thresholds.recoveryWait()), recoveryId);
                yield recoveryId;
            }
            case SHIPPING_DELAYED -> {
                paid(orderId);
                Long deliveryId = deliveryRepository.save(Delivery.create(orderId, SELLER_ID)).getId();
                jdbcTemplate.update("update orders.delivery set created_at = ? where id = ?",
                    since.minus(thresholds.shippingWait()), deliveryId);
                yield deliveryId;
            }
            case AUTO_CONFIRM_MISSED -> {
                paid(orderId);
                Long deliveryId = deliveryRepository.save(Delivery.create(orderId, SELLER_ID)).getId();
                jdbcTemplate.update("update orders.delivery set status = 'DELIVERED' where id = ?", deliveryId);
                jdbcTemplate.update("update orders.order_item set delivered_at = ? where id = ?",
                    since.minus(orderPolicy.autoConfirmAfter()).minus(thresholds.autoConfirmGrace()), itemId);
                yield itemId;
            }
            // 주문 하나가 대상인 종류가 아니다(아웃박스 맨 앞 메시지). OutboxStuckIntegrationTest가 본다
            case OUTBOX_STUCK -> throw new IllegalArgumentException("주문 대상 종류가 아님: " + kind);
        };
    }

    // 주문 · 주문상품 · 배송 · 복구 기록이 대상인 종류(아웃박스 멈춤은 OutboxStuckIntegrationTest)
    private static Stream<StuckOrderKind> orderKinds() {
        return Arrays.stream(StuckOrderKind.values()).filter(kind -> kind != StuckOrderKind.OUTBOX_STUCK);
    }

    private void paid(Long orderId) {
        jdbcTemplate.update("update orders.orders set status = 'PAID', paid_at = ? where id = ?", now, orderId);
    }

    private void cancel(Long orderId) {
        jdbcTemplate.update("update orders.orders set status = 'CANCELLED', cancel_reason = 'BUYER_CANCELLED' "
            + "where id = ?", orderId);
        jdbcTemplate.update("update orders.delivery set status = 'CANCELLED' where order_id = ? and status = 'READY'",
            orderId);
    }

    private static PaymentRecovery recoveryOf(Long orderId, boolean retryable) {
        return PaymentRecovery.open(orderId, 500L, BUYER_ID, 10_000L, List.of(), "DataIntegrityViolationException",
            retryable, LocalDateTime.now());
    }

    private static List<Long> targetIds(Page<StuckOrderResult> page) {
        return page.getContent().stream().map(StuckOrderResult::targetId).toList();
    }

    private List<Map<String, Object>> snapshot() {
        return jdbcTemplate.queryForList("""
            select 'order' as kind, id, status, version from orders.orders
            union all select 'item', id, status, version from orders.order_item
            union all select 'delivery', id, status, version from orders.delivery
            union all select 'recovery', id, status, version from orders.payment_recovery
            union all select 'history', count(*), null, null from orders.order_status_history
            order by 1, 2
            """);
    }
}
