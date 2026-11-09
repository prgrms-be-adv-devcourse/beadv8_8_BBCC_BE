package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.DeliveryInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.DeliveryCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryInboxServiceTest {

    private static final DeliveryCommand COMMAND = new DeliveryCommand(7L, 3L, 1L, "CJ대한통운", "123456789012",
        null);

    @Mock
    private InboxNotificationService inboxNotificationService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DeliveryInboxService deliveryInboxService;

    @Test
    @DisplayName("배송이 출발하면 구매자 알림함에 택배사 · 운송장과 주문 상세 주소를 넣는다")
    void shipped() {
        deliveryInboxService.addShipped(COMMAND);

        var info = captured();
        assertThat(info.recipient()).isEqualTo(InboxRecipient.member(1L));
        assertThat(info.type()).isEqualTo(NotificationType.DELIVERY_SHIPPED);
        assertThat(info.title()).isEqualTo("주문하신 상품이 출발했어요");
        assertThat(info.body()).isEqualTo("CJ대한통운 운송장 123456789012로 출발했어요.");
        assertThat(info.linkPath()).isEqualTo("/orders/3");
        assertThat(info.dedupKey()).isEqualTo("delivery.shipped:7");
    }

    @Test
    @DisplayName("택배사나 운송장이 없으면 null을 넣지 않고 운송장 없는 출발 문구를 넣는다")
    void shippedWithoutTracking() {
        deliveryInboxService.addShipped(new DeliveryCommand(7L, 3L, 1L, null, null, null));

        assertThat(captured().body()).isEqualTo("주문하신 상품이 출발했어요. 주문 상세에서 배송 상태를 확인해 주세요.");
    }

    @Test
    @DisplayName("자동 구매확정 예정 시각이 없는 예전 배송 완료는 날짜 없는 문구로 넣고, 출발과 다른 중복 키를 쓴다")
    void delivered() {
        deliveryInboxService.addDelivered(COMMAND);

        var info = captured();
        assertThat(info.type()).isEqualTo(NotificationType.DELIVERY_DELIVERED);
        assertThat(info.title()).isEqualTo("배송이 완료됐어요");
        assertThat(info.body()).isEqualTo("주문하신 상품이 도착했어요. 받은 상품을 확인해 주세요.");
        assertThat(info.linkPath()).isEqualTo("/orders/3");
        assertThat(info.dedupKey()).isEqualTo("delivery.delivered:7");
    }

    @Test
    @DisplayName("배송 완료에 자동 구매확정 예정 시각이 있으면 본문에 그 날짜를 넣는다")
    void deliveredWithAutoConfirmAt() {
        deliveryInboxService.addDelivered(new DeliveryCommand(7L, 3L, 1L, "CJ대한통운", "123456789012",
            LocalDateTime.of(2026, 11, 15, 9, 0)));

        var info = captured();
        assertThat(info.body()).isEqualTo("주문하신 상품이 도착했어요. 11월 15일에 자동으로 구매확정되니 그 전에 받은 상품을 확인해 주세요.");
        assertThat(info.dedupKey()).isEqualTo("delivery.delivered:7");
    }

    private InboxNotificationInfo captured() {
        var captor = ArgumentCaptor.forClass(InboxNotificationInfo.class);
        verify(inboxNotificationService).add(captor.capture());
        // 알림함과 같은 내용을 푸시로도 보낸다
        verify(notificationService).createPush(captor.getValue());
        return captor.getValue();
    }
}
