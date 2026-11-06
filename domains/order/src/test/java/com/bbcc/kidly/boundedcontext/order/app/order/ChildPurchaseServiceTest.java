package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.ChildPurchase;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeliveries;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository.ChildPurchaseRow;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * 자녀 구매 내역 상태(#1000): 취소 → 구매확정 → 배송 완료 → 배송 중(그 판매자 배송이 발송됨) → 결제 완료. 배송은 한 페이지에 한 번 읽는다.
 */
class ChildPurchaseServiceTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 6, 10, 0);

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderDeliveries orderDeliveries = mock(OrderDeliveries.class);
    private final ChildPurchaseService service = new ChildPurchaseService(orderRepository, orderDeliveries);

    @Test
    @DisplayName("주문상품 · 주문 · 배송으로 상태를 정한다. 배송은 아직 배송 완료 전인 주문만 한 번에 읽는다")
    void statuses() {
        List<ChildPurchaseRow> rows = List.of(
            row(1L, 10L, 2L, OrderItem.Status.ORDERED, Order.Status.PAID, null),
            row(2L, 10L, 3L, OrderItem.Status.ORDERED, Order.Status.PAID, null),
            row(3L, 11L, 2L, OrderItem.Status.ORDERED, Order.Status.PAID, AT),
            row(4L, 12L, 2L, OrderItem.Status.CONFIRMED, Order.Status.PAID, AT),
            row(5L, 13L, 2L, OrderItem.Status.CANCELLED, Order.Status.PAID, null),
            row(6L, 14L, 2L, OrderItem.Status.ORDERED, Order.Status.CANCELLED, null));
        when(orderRepository.findChildPurchases(eq(7L), any())).thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20),
            rows.size()));
        when(orderDeliveries.findByOrders(List.of(10L))).thenReturn(List.of(
            new OrderDeliveries.Delivery(1L, 10L, 2L, "SHIPPED", "CJ", "1", AT, null, true),
            new OrderDeliveries.Delivery(2L, 10L, 3L, "READY", null, null, null, null, false)));

        List<ChildPurchase> lines = service.getPurchases(7L, 0, 20).getContent();

        assertThat(lines).extracting(ChildPurchase::status)
            .containsExactly("SHIPPING", "PAID", "DELIVERED", "CONFIRMED", "CANCELLED", "CANCELLED");
        assertThat(lines).extracting(ChildPurchase::orderItemId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    @DisplayName("배송 완료 전인 줄이 없으면 배송을 읽지 않는다")
    void noDeliveryLookupWhenNotNeeded() {
        List<ChildPurchaseRow> rows = List.of(row(4L, 12L, 2L, OrderItem.Status.CONFIRMED, Order.Status.PAID, AT));
        when(orderRepository.findChildPurchases(eq(7L), any())).thenReturn(new PageImpl<>(rows));

        assertThat(service.getPurchases(7L, 0, 20).getContent()).extracting(ChildPurchase::status)
            .containsExactly("CONFIRMED");
        verify(orderDeliveries, never()).findByOrders(any());
    }

    private static ChildPurchaseRow row(Long itemId, Long orderId, Long sellerId, OrderItem.Status itemStatus,
        Order.Status orderStatus, LocalDateTime deliveredAt) {
        return new ChildPurchaseRow() {
            @Override
            public Long getOrderItemId() {
                return itemId;
            }

            @Override
            public Long getOrderId() {
                return orderId;
            }

            @Override
            public LocalDateTime getOrderedAt() {
                return AT;
            }

            @Override
            public Long getProductId() {
                return 1L;
            }

            @Override
            public Long getSellerId() {
                return sellerId;
            }

            @Override
            public String getProductName() {
                return "상품";
            }

            @Override
            public String getOptionName() {
                return "옵션";
            }

            @Override
            public int getQuantity() {
                return 1;
            }

            @Override
            public OrderItem.Status getItemStatus() {
                return itemStatus;
            }

            @Override
            public Order.Status getOrderStatus() {
                return orderStatus;
            }

            @Override
            public LocalDateTime getDeliveredAt() {
                return deliveredAt;
            }
        };
    }
}
