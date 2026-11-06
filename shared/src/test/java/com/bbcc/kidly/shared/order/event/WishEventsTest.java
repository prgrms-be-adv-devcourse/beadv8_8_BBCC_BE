package com.bbcc.kidly.shared.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 조르기 이벤트 4종(#702): 이벤트 타입 · 발행 도메인 · 대상(조르기 ID, 담김은 줄 ID) · 흐름(키 = 자녀 프로필 ID) · data 칸.
 */
class WishEventsTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 2, 15, 0);
    private static final Long CHILD_ID = 7L;
    private static final Long MEMBER_ID = 1L;

    @Test
    @DisplayName("이벤트 타입은 wish.item-added · wish.sent · wish.approved · wish.rejected · wish.cancelled · "
        + "wish.paid(#999), 발행은 주문 도메인, "
        + "키는 자녀 프로필 ID")
    void metadata() {
        List<DomainEvent> events = List.of(
            new WishItemAddedEvent(UUID.randomUUID(), AT, new WishItemAddedEvent.Data(40L, CHILD_ID, MEMBER_ID,
                List.of(new WishItemAddedEvent.Item(10L, 3L, 2)))),
            new WishSentEvent(UUID.randomUUID(), AT, new WishSentEvent.Data(50L, CHILD_ID, MEMBER_ID,
                List.of(new WishSentEvent.Item(10L, 3L, 2)))),
            new WishApprovedEvent(UUID.randomUUID(), AT, new WishApprovedEvent.Data(50L, CHILD_ID, MEMBER_ID,
                List.of(new WishApprovedEvent.Item(10L, 3L, 1)))),
            new WishRejectedEvent(UUID.randomUUID(), AT, new WishRejectedEvent.Data(50L, CHILD_ID, MEMBER_ID,
                List.of(new WishRejectedEvent.Item(10L, 3L, 2)), "TOO_EXPENSIVE")),
            new WishCancelledEvent(UUID.randomUUID(), AT, new WishCancelledEvent.Data(50L, CHILD_ID, MEMBER_ID,
                List.of(new WishCancelledEvent.Item(10L, 3L, 2)))),
            new WishPaidEvent(UUID.randomUUID(), AT, new WishPaidEvent.Data(50L, CHILD_ID, MEMBER_ID,
                List.of(new WishPaidEvent.Item(10L, 3L, 2)), "MEMBER", MEMBER_ID, 90L)));

        assertThat(events).extracting(DomainEvent::eventType)
            .containsExactly("wish.item-added", "wish.sent", "wish.approved", "wish.rejected", "wish.cancelled",
                "wish.paid");
        assertThat(events).extracting(DomainEvent::producer).containsOnly("order");
        assertThat(events).extracting(DomainEvent::aggregateId).containsExactly("40", "50", "50", "50", "50", "50");
        assertThat(events).extracting(DomainEvent::correlationId).containsOnly(String.valueOf(CHILD_ID));
        assertThat(events).extracting(DomainEvent::schemaVersion).containsOnly(1);
    }

    @Test
    @DisplayName("data 칸: 대상 ID · 자녀 프로필 ID · 보호자 회원 ID · items[productId · optionId · quantity] (+ 거절 사유)")
    void fields() {
        assertThat(names(WishItemAddedEvent.Data.class)).containsExactly("wishItemId", "childId", "memberId", "items");
        assertThat(names(WishSentEvent.Data.class)).containsExactly("wishRequestId", "childId", "memberId", "items");
        assertThat(names(WishApprovedEvent.Data.class))
            .containsExactly("wishRequestId", "childId", "memberId", "items");
        assertThat(names(WishRejectedEvent.Data.class))
            .containsExactly("wishRequestId", "childId", "memberId", "items", "reason");
        assertThat(names(WishCancelledEvent.Data.class))
            .containsExactly("wishRequestId", "childId", "memberId", "items", "cancelReason");
        assertThat(names(WishPaidEvent.Data.class))
            .containsExactly("wishRequestId", "childId", "memberId", "items", "payerType", "payerId", "orderId");
        assertThat(List.of(WishItemAddedEvent.Item.class, WishSentEvent.Item.class, WishApprovedEvent.Item.class,
            WishRejectedEvent.Item.class, WishCancelledEvent.Item.class, WishPaidEvent.Item.class))
            .allSatisfy(item -> assertThat(names(item)).containsExactly("productId", "optionId", "quantity"));
    }

    @Test
    @DisplayName("원인 칸이 없던 예전 철회 모양(#968)으로 만들면 cancelReason은 null이다(받는 쪽은 DIRECT로 읽는다)")
    void legacyCancelled() {
        assertThat(new WishCancelledEvent.Data(50L, CHILD_ID, MEMBER_ID, List.of()).cancelReason()).isNull();
    }

    private static List<String> names(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
