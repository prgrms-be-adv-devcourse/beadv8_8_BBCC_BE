package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.ProductNameService;
import com.bbcc.kidly.boundedcontext.notification.app.SellerOrderInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderPaidCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.PurchaseConfirmedCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerOrderInboxServiceTest {

    @Mock
    private InboxNotificationService inboxNotificationService;

    @Mock
    private ProductNameService productNameService;

    @InjectMocks
    private SellerOrderInboxService sellerOrderInboxService;

    @Test
    @DisplayName("결제완료된 주문은 판매자마다 자기 상품으로 한 건씩 알림함에 넣는다")
    void orderPaidPerSeller() {
        sellerOrderInboxService.addOrderPaid(new OrderPaidCommand(UUID.randomUUID(), 3L, 1L, 30_000L, List.of(
            new OrderPaidCommand.Item(5L, "곰돌이 티셔츠", "네이비 / 110", 1, 10_000L),
            new OrderPaidCommand.Item(5L, "공룡 운동화", null, 1, 15_000L),
            new OrderPaidCommand.Item(6L, "토끼 양말", null, 1, 5_000L))));

        var captor = ArgumentCaptor.forClass(InboxNotificationInfo.class);
        verify(inboxNotificationService, times(2)).add(captor.capture());
        assertThat(captor.getAllValues()).satisfiesExactly(
            info -> {
                assertThat(info.memberId()).isEqualTo(5L);
                assertThat(info.type()).isEqualTo(NotificationType.SELLER_ORDER_PAID);
                assertThat(info.body()).isEqualTo("곰돌이 티셔츠 외 1건 주문이 결제됐어요. 발송을 준비해 주세요.");
                assertThat(info.dedupKey()).isEqualTo("order.paid:3:5");
            },
            info -> {
                assertThat(info.memberId()).isEqualTo(6L);
                assertThat(info.body()).isEqualTo("토끼 양말 주문이 결제됐어요. 발송을 준비해 주세요.");
                assertThat(info.dedupKey()).isEqualTo("order.paid:3:6");
            });
    }

    @Test
    @DisplayName("구매확정된 주문상품은 판매자 알림함에 상품 이름과 함께 넣는다")
    void purchaseConfirmed() {
        given(productNameService.findName(9L)).willReturn(Optional.of("곰돌이 티셔츠"));

        sellerOrderInboxService.addPurchaseConfirmed(new PurchaseConfirmedCommand(7L, 3L, 1L, 5L, 9L, 2, "DIRECT"));

        var info = captured();
        assertThat(info.memberId()).isEqualTo(5L);
        assertThat(info.type()).isEqualTo(NotificationType.SELLER_PURCHASE_CONFIRMED);
        assertThat(info.body()).isEqualTo("곰돌이 티셔츠 2개가 구매확정돼 정산 대상이 됐어요.");
        assertThat(info.dedupKey()).isEqualTo("order-item.purchase-confirmed:7");
    }

    @Test
    @DisplayName("상품 이름 복사본이 없으면 이름 없이 넣는다")
    void purchaseConfirmedWithoutName() {
        given(productNameService.findName(9L)).willReturn(Optional.empty());

        sellerOrderInboxService.addPurchaseConfirmed(new PurchaseConfirmedCommand(7L, 3L, 1L, 5L, 9L, 2, "DIRECT"));

        assertThat(captured().body()).isEqualTo("주문상품 2개가 구매확정돼 정산 대상이 됐어요.");
    }

    private InboxNotificationInfo captured() {
        var captor = ArgumentCaptor.forClass(InboxNotificationInfo.class);
        verify(inboxNotificationService).add(captor.capture());
        return captor.getValue();
    }
}
