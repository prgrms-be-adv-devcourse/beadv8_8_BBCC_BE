package com.bbcc.kidly.boundedcontext.notification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.notification.app.AutoConfirmInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.DeliveryInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.OrderCancelInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.SellerOrderInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.WishInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.AutoConfirmScheduledCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.ChildWishCommand;
import com.bbcc.kidly.boundedcontext.notification.in.event.NotificationOrderEventListener;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문이 아직 보내지 않는 이벤트(#1018)를 주문에 요청한 메시지 형식(공통 칸 7개 + data) 그대로 읽는지 본다.
 * data의 모르는 칸(memberId · optionId 등)은 무시한다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationOrderEventListenerTest {

    @Mock
    private WishInboxService wishInboxService;

    @Mock
    private AutoConfirmInboxService autoConfirmInboxService;

    @Mock
    private OrderCancelInboxService orderCancelInboxService;

    @Mock
    private DeliveryInboxService deliveryInboxService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SellerOrderInboxService sellerOrderInboxService;

    private NotificationOrderEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationOrderEventListener(wishInboxService, orderCancelInboxService,
            deliveryInboxService, notificationService, sellerOrderInboxService, autoConfirmInboxService,
            JsonMapper.builder().build());
    }

    @Test
    @DisplayName("wish.paid는 결제한 상품으로 자녀 결제 완료 알림을 만든다")
    void wishPaid() {
        listener.on(record("wish.paid", "{\"wishRequestId\":7,\"childId\":3,\"memberId\":1,\"orderId\":40,"
            + "\"items\":[{\"productId\":null,\"optionId\":5,\"quantity\":1},"
            + "{\"productId\":10,\"optionId\":6,\"quantity\":2}]}"));

        then(wishInboxService).should().addWishPaid(new ChildWishCommand(7L, 3L, Arrays.asList(null, 10L)));
    }

    @Test
    @DisplayName("wish.expired는 조른 상품으로 자녀 만료 알림을 만든다")
    void wishExpired() {
        listener.on(record("wish.expired", "{\"wishRequestId\":7,\"childId\":3,\"memberId\":1,"
            + "\"items\":[{\"productId\":10,\"optionId\":5,\"quantity\":1}]}"));

        then(wishInboxService).should().addWishExpired(new ChildWishCommand(7L, 3L, Arrays.asList(10L)));
    }

    @Test
    @DisplayName("order-item.auto-confirm-scheduled는 확정 예정 시각으로 구매자 예고 알림을 만든다")
    void autoConfirmScheduled() {
        listener.on(record("order-item.auto-confirm-scheduled", "{\"orderItemId\":7,\"orderId\":3,\"memberId\":1,"
            + "\"sellerId\":5,\"productId\":9,\"quantity\":2,\"confirmAt\":\"2026-11-20T00:00:00\"}"));

        then(autoConfirmInboxService).should().addAutoConfirmScheduled(
            new AutoConfirmScheduledCommand(7L, 3L, 1L, 9L, 2, LocalDateTime.of(2026, 11, 20, 0, 0)));
    }

    private static ConsumerRecord<String, String> record(String eventType, String data) {
        var body = "{\"eventId\":\"6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90\",\"eventType\":\"" + eventType
            + "\",\"schemaVersion\":1,\"occurredAt\":\"2026-11-08T09:00:00\",\"producer\":\"order\","
            + "\"aggregateId\":\"7\",\"correlationId\":\"3\",\"data\":" + data + "}";
        var record = new ConsumerRecord<>("kidly.order.events", 0, 0L, "3", body);
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        return record;
    }
}
