package com.bbcc.kidly.boundedcontext.order.app.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.delivery.dto.DeliveryResult;
import com.bbcc.kidly.boundedcontext.order.app.delivery.dto.SellerOrderView;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    private static final Long ORDER_ID = 7L;
    private static final Long DELIVERY_ID = 5L;
    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_A_ID = 10L;
    private static final Long SELLER_B_ID = 20L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 9, 0);

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private DeliveryService deliveryService;

    @Captor
    private ArgumentCaptor<Delivery> saved;

    @Nested
    @DisplayName("결제완료 뒤 판매자별 주문 만들기")
    class CreateFor {

        @Test
        @DisplayName("주문상품을 판매자로 묶어 판매자마다 배송 준비 1건을 만든다")
        void onePerSeller() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOfTwoSellers()));

            deliveryService.createFor(ORDER_ID);

            verify(deliveryRepository, times(2)).save(saved.capture());
            assertThat(saved.getAllValues()).hasSize(2);
            assertThat(saved.getAllValues().get(0).getSellerId()).isEqualTo(SELLER_A_ID);
            assertThat(saved.getAllValues().get(1).getSellerId()).isEqualTo(SELLER_B_ID);
            assertThat(saved.getAllValues()).allSatisfy(delivery -> {
                assertThat(delivery.getOrderId()).isEqualTo(ORDER_ID);
                assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.READY);
            });
        }

        @Test
        @DisplayName("주문이 없으면 만들지 않는다")
        void ignoresMissingOrder() {
            deliveryService.createFor(ORDER_ID);

            verify(deliveryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("발송")
    class Ship {

        @Test
        @DisplayName("택배사 · 송장을 기록하고 배송 중이 된다")
        void ships() {
            Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
            when(deliveryRepository.findByIdAndSellerId(DELIVERY_ID, SELLER_A_ID)).thenReturn(Optional.of(delivery));

            DeliveryResult result = deliveryService.ship(SELLER_A_ID, DELIVERY_ID, "CJ", "1234567890");

            assertThat(result.status()).isEqualTo("SHIPPING");
            assertThat(result.carrier()).isEqualTo("CJ");
            assertThat(result.trackingNumber()).isEqualTo("1234567890");
            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            assertThat(result.sellerId()).isEqualTo(SELLER_A_ID);
            assertThat(result.shippedAt()).isNotNull();
        }

        @Test
        @DisplayName("없거나 남의 배송이면 DELIVERY_NOT_FOUND")
        void othersDeliveryIsNotFound() {
            assertThatThrownBy(() -> deliveryService.ship(SELLER_B_ID, DELIVERY_ID, "CJ", "1234567890"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.DELIVERY_NOT_FOUND);
            verify(deliveryRepository).findByIdAndSellerId(DELIVERY_ID, SELLER_B_ID);
        }

        @Test
        @DisplayName("모르는 택배사면 INVALID_CARRIER")
        void unknownCarrier() {
            assertThatThrownBy(() -> deliveryService.ship(SELLER_A_ID, DELIVERY_ID, "UNKNOWN", "1234567890"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.INVALID_CARRIER);
        }
    }

    @Nested
    @DisplayName("배송 완료")
    class Complete {

        @Test
        @DisplayName("배송 완료로 바꾸고 주문 ID · 판매자 ID · 완료 시각을 돌려준다")
        void completes() {
            Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
            delivery.ship(Delivery.Carrier.CJ, "1234567890", NOW);
            when(deliveryRepository.findByIdAndSellerId(DELIVERY_ID, SELLER_A_ID)).thenReturn(Optional.of(delivery));

            DeliveryResult result = deliveryService.complete(SELLER_A_ID, DELIVERY_ID);

            assertThat(result.status()).isEqualTo("DELIVERED");
            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            assertThat(result.sellerId()).isEqualTo(SELLER_A_ID);
            assertThat(result.deliveredAt()).isNotNull();
        }

        @Test
        @DisplayName("없거나 남의 배송이면 DELIVERY_NOT_FOUND")
        void othersDeliveryIsNotFound() {
            assertThatThrownBy(() -> deliveryService.complete(SELLER_B_ID, DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.DELIVERY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("판매자 주문 목록")
    class SellerOrders {

        @Test
        @DisplayName("판매자의 배송을 최근 것부터 요청한 페이지로, 그 판매자 몫 주문상품 · 배송지와 함께 돌려준다")
        void onlySellersItems() {
            Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
            when(deliveryRepository.findAllBySellerIdOrderByIdDesc(SELLER_A_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(delivery), PageRequest.of(0, 20), 1));
            when(orderRepository.findAllWithItemsByIdIn(List.of(ORDER_ID)))
                .thenReturn(List.of(withId(orderOfTwoSellers(), ORDER_ID)));

            Page<SellerOrderView> page = deliveryService.getSellerOrders(SELLER_A_ID, 0, 20);

            assertThat(page.getContent()).hasSize(1);
            SellerOrderView view = page.getContent().get(0);
            assertThat(view.orderId()).isEqualTo(ORDER_ID);
            assertThat(view.status()).isEqualTo("READY");
            assertThat(view.shippingAddress().recipientName()).isEqualTo("홍길동");
            assertThat(view.items()).hasSize(2);
            assertThat(view.items()).allSatisfy(item -> assertThat(item.sellerId()).isEqualTo(SELLER_A_ID));
            verify(orderRepository, never()).findById(any());
        }

        @Test
        @DisplayName("배송의 주문이 없으면 IllegalStateException")
        void missingOrder() {
            Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
            when(deliveryRepository.findAllBySellerIdOrderByIdDesc(SELLER_A_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(delivery), PageRequest.of(0, 20), 1));
            when(orderRepository.findAllWithItemsByIdIn(List.of(ORDER_ID))).thenReturn(List.of());

            assertThatThrownBy(() -> deliveryService.getSellerOrders(SELLER_A_ID, 0, 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("배송의 주문이 없습니다: " + ORDER_ID);
        }

        @Test
        @DisplayName("배송이 없는 페이지면 주문을 조회하지 않는다")
        void emptyPage() {
            when(deliveryRepository.findAllBySellerIdOrderByIdDesc(SELLER_A_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            Page<SellerOrderView> page = deliveryService.getSellerOrders(SELLER_A_ID, 0, 20);

            assertThat(page.getContent()).isEmpty();
            verify(orderRepository, never()).findAllWithItemsByIdIn(any());
        }
    }

    private static Order withId(Order order, Long id) {
        try {
            java.lang.reflect.Field field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
            return order;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 판매자 A 상품 2줄 · 판매자 B 상품 1줄.
     */
    private static Order orderOfTwoSellers() {
        return Order.place(BUYER_ID, List.of(
                line(1L, 1L, SELLER_A_ID),
                line(1L, 2L, SELLER_A_ID),
                line(2L, 3L, SELLER_B_ID)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), NOW);
    }

    private static Order.RequestedItem line(Long productId, Long optionId, Long sellerId) {
        return new Order.RequestedItem(
            new OrderItem.ProductSnapshot(productId, optionId, sellerId, "상품", "110", 10_000L, 1L),
            true, null, 1, 10_000L);
    }
}
