package com.bbcc.kidly.boundedcontext.wish.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.wish.app.WishPurchaseService;
import com.bbcc.kidly.boundedcontext.wish.app.WishPurchaseService.PurchasedLine;
import com.bbcc.kidly.shared.order.event.CartItemPurchasedEvent;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 모듈 자기 토픽(kidly.order.events)에서 장바구니 구매 사실(cart.item-purchased, 예약 ID 있음)을 받아 조르기 결제
 * 완료로 넘긴다(#999, 참모 결정 ①). 결제 정리 트랜잭션과 떨어져 있어 조르기 처리가 실패해도 결제는 되돌아가지 않는다.
 * 자기 토픽을 읽는 것이고 받은 메시지를 다시 넣지 않는다(루트 7절과 무관). 다른 종류는 본문을 읽지 않고 건너뛴다.
 * <ul>
 *   <li>구독 그룹 · 컨테이너 팩토리는 주문 모듈 공용 Kafka 수신 기반 값(그룹 kidly.order, 팩토리 orderKafkaListenerContainerFactory:
 *       한 건씩 완료 · earliest · 1초 간격 3번 재시도 → DLT kidly.order.events.order.dlt · 수신 스위치)을 이름으로 쓴다.
 *       조르기는 주문 컨텍스트 코드를 참조하지 않으므로(컨텍스트 규칙) 값만 같게 둔다(같은지는 테스트가 본다)</li>
 *   <li>처리는 {@link WishPurchaseService}의 트랜잭션에서 끝나고, 실패하면 예외를 그대로 던져 재시도 → DLT로 보낸다.
 *       같은 메시지를 다시 받아도 결과는 1회분이다</li>
 *   <li>저장한 DLT 메시지 다시 처리(#601)는 주문의 다시 처리기가 이 빈을 {@code Consumer<ConsumerRecord>}로 받아 부른다
 *       (컨텍스트 규칙상 타입으로 잇지 않는다)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishCartEventKafkaListener implements Consumer<ConsumerRecord<String, String>> {

    // 주문 Kafka 리스너 이름 규칙: order + 받는 것 + KafkaListener
    public static final String ID = "orderWishCartEventKafkaListener";
    public static final String TOPIC = "kidly.order.events";
    public static final String GROUP = "kidly.order";
    public static final String FACTORY = "orderKafkaListenerContainerFactory";
    static final String EVENT_TYPE_HEADER = "eventType";
    static final String CART_ITEM_PURCHASED = "cart.item-purchased";

    private final JsonMapper jsonMapper;
    private final WishPurchaseService wishPurchaseService;

    @KafkaListener(id = ID, topics = TOPIC, groupId = GROUP, containerFactory = FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(EVENT_TYPE_HEADER);
        if (header == null) {
            // 읽지 못한 메시지: 재시도 없이 DLT(주문 리스너와 같음). 본문은 남기지 않는다
            throw new IllegalArgumentException("eventType 헤더 없음: " + record.topic() + "-" + record.partition() + "@"
                + record.offset());
        }
        if (!CART_ITEM_PURCHASED.equals(new String(header.value(), UTF_8))) {
            return;
        }
        onCartItemPurchased(jsonMapper.readValue(record.value(), CartItemPurchasedEvent.class));
    }

    @Override
    public void accept(ConsumerRecord<String, String> record) {
        on(record);
    }

    void onCartItemPurchased(CartItemPurchasedEvent event) {
        PurchasedLine line = toLine(event);
        if (line != null) {
            wishPurchaseService.onPurchased(line);
        }
    }

    // 예약 칸이 아니었으면(예약 ID 없음) 조르기와 무관하다(null)
    static PurchasedLine toLine(CartItemPurchasedEvent event) {
        CartItemPurchasedEvent.Data data = event.data();
        if (data == null || data.reservationId() == null) {
            return null;
        }
        return new PurchasedLine(data.cartItemId(), data.orderId(), data.orderItemId(), data.memberId(),
            data.optionId(), data.quantity(), data.reservationId());
    }
}
