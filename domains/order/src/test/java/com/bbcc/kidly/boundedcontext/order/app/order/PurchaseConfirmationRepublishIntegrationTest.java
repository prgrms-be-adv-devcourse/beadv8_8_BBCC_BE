package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class PurchaseConfirmationRepublishIntegrationTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.now().minusDays(60).withNano(0);
    private static final String BREAK_OUTBOX = "test_741_break_republish_outbox";

    @Autowired
    private OrderRepository orders;

    @Autowired
    private PurchaseConfirmationRepublishService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper mapper;

    @AfterEach
    void restore() {
        jdbc.execute("alter table orders.order_outbox drop constraint if exists " + BREAK_OUTBOX);
    }

    @Test
    @DisplayName("이전 아웃박스와 DLT가 없어도 오래된 확정 사실의 원래 값으로 이벤트를 재구성한다")
    void reconstructsOldFactsWithoutDeliveryHistory() {
        Long itemId = save("CONFIRMED");
        assertThat(jdbc.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();
        assertThat(service.republish(0, null, 100, 99L).published()).isOne();
        String payload = jdbc.queryForObject("select payload from orders.order_outbox", String.class);
        var event = mapper.readValue(payload, OrderItemPurchaseConfirmedEvent.class);
        assertThat(event.data().orderItemId()).isEqualTo(itemId);
        assertThat(event.data().memberId()).isEqualTo(13L);
        assertThat(event.data().sellerId()).isEqualTo(2L);
        assertThat(event.data().productId()).isEqualTo(3L);
        assertThat(event.data().optionId()).isEqualTo(4L);
        assertThat(event.data().size()).isEqualTo("110");
        assertThat(event.data().quantity()).isEqualTo(2);
        assertThat(event.data().amount()).isEqualTo(2000L);
        assertThat(event.data().childId()).isNull();
        assertThat(event.data().confirmedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(event.data().confirmType()).as("저장된 확정 방식(#901)").isEqualTo("DIRECT");
        assertThat(event.occurredAt()).isEqualTo(CONFIRMED_AT);
        assertThat(jdbc.queryForObject("select message_key from orders.order_outbox", String.class))
            .isEqualTo(event.data().orderId().toString());
    }

    @Test
    @DisplayName("확정 방식은 저장값 그대로다(#901): AUTO는 AUTO, 저장값이 없는 옛 행은 null")
    void republishesStoredConfirmType() {
        Long auto = save("CONFIRMED");
        Long legacy = save("CONFIRMED");
        jdbc.update("update orders.order_item set confirm_type = 'AUTO' where id = ?", auto);
        jdbc.update("update orders.order_item set confirm_type = null where id = ?", legacy);

        assertThat(service.republish(0, null, 100, 99L).published()).isEqualTo(2);

        assertThat(jdbc.queryForList("select payload from orders.order_outbox order by id", String.class))
            .extracting(payload -> mapper.readValue(payload, OrderItemPurchaseConfirmedEvent.class).data())
            .extracting(OrderItemPurchaseConfirmedEvent.Data::orderItemId,
                OrderItemPurchaseConfirmedEvent.Data::confirmType)
            .containsExactly(tuple(auto, "AUTO"), tuple(legacy, null));
    }

    @Test
    @DisplayName("커서와 첫 상한을 유지해 배치 사이 새로 확정된 높은 ID를 섞지 않는다")
    void boundedCursorBatches() {
        Long first = save("CONFIRMED");
        Long second = save("CONFIRMED");
        Long last = save("CONFIRMED");
        var page = service.republish(0, null, 1, 99L);
        assertThat(page.nextAfterOrderItemId()).isEqualTo(first);
        assertThat(page.throughOrderItemId()).isEqualTo(last);
        save("CONFIRMED");
        var middle = service.republish(first, page.throughOrderItemId(), 1, 99L);
        assertThat(middle.nextAfterOrderItemId()).isEqualTo(second);
        var end = service.republish(second, page.throughOrderItemId(), 1, 99L);
        assertThat(end.hasNext()).isFalse();
        assertThat(end.nextAfterOrderItemId()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from orders.order_outbox", Long.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("확정되지 않았거나 취소된 상품은 재발행하지 않고 같은 사실의 재전달도 주문 상태를 바꾸지 않는다")
    void selectsOnlyConfirmedFacts() {
        Long confirmed = save("CONFIRMED");
        save("ORDERED");
        save("CANCELLED");
        service.republish(0, null, 100, 99L);
        service.republish(0, null, 100, 99L);
        var rows = jdbc.queryForList("select payload from orders.order_outbox order by id", String.class);
        assertThat(rows).hasSize(2);
        var one = mapper.readValue(rows.getFirst(), OrderItemPurchaseConfirmedEvent.class);
        var two = mapper.readValue(rows.getLast(), OrderItemPurchaseConfirmedEvent.class);
        assertThat(one.eventId()).isNotEqualTo(two.eventId());
        assertThat(one.data()).isEqualTo(two.data());
        assertThat(jdbc.queryForObject("select status from orders.order_item where id = ?", String.class, confirmed))
            .isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("아웃박스 저장 실패는 배치 전체를 롤백한다")
    void failedBatchRollsBack() {
        save("CONFIRMED");
        save("CONFIRMED");
        jdbc.execute("alter table orders.order_outbox add constraint " + BREAK_OUTBOX + " check (false) not valid");
        assertThatThrownBy(() -> service.republish(0, null, 100, 99L))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();
    }

    private Long save(String status) {
        Order order = orders.save(Order.place(13L, List.of(new Order.RequestedItem(
            new OrderItem.ProductSnapshot(3L, 4L, 2L, "상품", "옵션", "110", 1000L, 5L), true, null, 2, 1000L)),
            new Order.ShippingAddress("수령인", "01012345678", "12345", "주소"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), CONFIRMED_AT.minusDays(3)));
        Long itemId = order.getItems().getFirst().getId();
        jdbc.update("update orders.orders set status = 'PAID', payment_id = 500, paid_at = ? where id = ?",
            CONFIRMED_AT.minusDays(2), order.getId());
        jdbc.update("update orders.order_item set status = ?, delivered_at = ?, confirmed_at = ?, "
            + "confirm_type = 'DIRECT' where id = ?", status, CONFIRMED_AT.minusDays(1), CONFIRMED_AT, itemId);
        return itemId;
    }
}
