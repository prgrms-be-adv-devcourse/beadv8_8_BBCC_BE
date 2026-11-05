package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.OrderCancelInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderCancelledCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderItemCancelledCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCancelInboxServiceTest {

    @Mock
    private InboxNotificationService inboxNotificationService;

    private OrderCancelInboxService orderCancelInboxService;

    @BeforeEach
    void setUp() {
        orderCancelInboxService = new OrderCancelInboxService(inboxNotificationService);
    }

    @Test
    @DisplayName("재고 부족으로 주문이 취소되면 구매자 알림함에 환불 안내와 주문 상세 주소를 넣는다")
    void outOfStock() {
        orderCancelInboxService.addOrderCancelled(new OrderCancelledCommand(3L, 1L, "OUT_OF_STOCK"));

        var info = captured();
        assertThat(info.memberId()).isEqualTo(1L);
        assertThat(info.type()).isEqualTo(NotificationType.ORDER_CANCELLED);
        assertThat(info.title()).isEqualTo("재고가 없어 주문이 취소됐어요");
        assertThat(info.linkPath()).isEqualTo("/orders/3");
        assertThat(info.dedupKey()).isEqualTo("order.cancelled:3");
    }

    @Test
    @DisplayName("결제 기한이 지나 주문이 취소되면 알림함에 넣는다")
    void paymentExpired() {
        orderCancelInboxService.addOrderCancelled(new OrderCancelledCommand(3L, 1L, "PAYMENT_EXPIRED"));

        assertThat(captured().title()).isEqualTo("결제 기한이 지나 주문이 취소됐어요");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUYER_CANCELLED", "PAYMENT_FAILED"})
    @DisplayName("구매자가 직접 취소했거나 결제가 실패한 주문은 그 화면에서 결과를 보므로 넣지 않는다")
    void skipSeenOnScreen(String reason) {
        orderCancelInboxService.addOrderCancelled(new OrderCancelledCommand(3L, 1L, reason));

        verify(inboxNotificationService, never()).add(any());
    }

    @Test
    @DisplayName("판매자가 주문 상품을 취소하면 개수와 사유를 넣고, 같은 취소는 취소 ID 중복 키로 한 건이다")
    void cancelledBySeller() {
        orderCancelInboxService.addItemsCancelledBySeller(
            new OrderItemCancelledCommand(9L, 3L, 1L, "WRONG_PRODUCT_INFO", 2));

        var info = captured();
        assertThat(info.type()).isEqualTo(NotificationType.ORDER_ITEM_CANCELLED);
        assertThat(info.body()).isEqualTo("상품 2개가 취소됐어요. 사유: 상품 정보 오류. 금액은 예치금으로 돌려드려요.");
        assertThat(info.linkPath()).isEqualTo("/orders/3");
        assertThat(info.dedupKey()).isEqualTo("order-item.cancelled:9");
    }

    private InboxNotificationInfo captured() {
        var info = ArgumentCaptor.forClass(InboxNotificationInfo.class);
        verify(inboxNotificationService).add(info.capture());
        return info.getValue();
    }
}
