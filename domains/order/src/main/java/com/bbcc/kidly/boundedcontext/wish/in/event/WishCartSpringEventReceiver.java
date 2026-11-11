package com.bbcc.kidly.boundedcontext.wish.in.event;

import com.bbcc.kidly.boundedcontext.wish.app.WishPurchaseService;
import com.bbcc.kidly.boundedcontext.wish.app.WishPurchaseService.PurchasedLine;
import com.bbcc.kidly.shared.order.event.CartItemPurchasedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 Kafka 수신 스위치(kidly.order.kafka.consumer.enabled)가 꺼져 있을 때만 장바구니 구매 사실을 Spring 이벤트로 받는다(#999).
 * 조르기 빈은 모두 조르기 스위치 하나로만 켜고 끄므로(WishSwitchTest) 수신 스위치는 빈 조건이 아니라 값으로 본다.
 * 주문의 다른 수신(SpringEventReceiver)과 같은 되돌리기 경로이고, 테스트 설정도 꺼져 있어 기능 테스트는 이 경로로 돈다.
 * 결제 정리 트랜잭션이 커밋된 뒤 새 트랜잭션에서 처리하므로 결제 완료와 떨어져 있다(실패해도 결제는 되돌아가지 않는다).
 * 되돌리기 경로에는 재시도 · DLT가 없으므로 실패는 eventType · eventId · 예외 종류만 남기고 삼킨다(결제 쪽으로 번지지 않게).
 * 켜져 있으면(기본) Kafka 리스너({@link WishCartEventKafkaListener})만 받는다(이중 처리 없음).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishCartSpringEventReceiver {

    private final WishPurchaseService wishPurchaseService;
    private final boolean kafkaReceiving;

    public WishCartSpringEventReceiver(WishPurchaseService wishPurchaseService,
        @Value("${kidly.order.kafka.consumer.enabled:true}") boolean kafkaReceiving) {
        this.wishPurchaseService = wishPurchaseService;
        this.kafkaReceiving = kafkaReceiving;
    }

    // 처리는 WishPurchaseService가 새 트랜잭션(REQUIRES_NEW)에서 한다. 여기서 트랜잭션을 열면 처리 실패가 이 트랜잭션을 롤백 전용으로
    // 만들어, 예외를 삼켜도 커밋 때 다시 예외가 난다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCartItemPurchased(CartItemPurchasedEvent event) {
        PurchasedLine line = kafkaReceiving ? null : WishCartEventKafkaListener.toLine(event);
        if (line == null) {
            return;
        }
        try {
            wishPurchaseService.onPurchased(line);
        } catch (RuntimeException e) {
            log.error("조르기 결제 완료를 반영하지 못했습니다. eventType={} eventId={} exception={}", event.eventType(),
                event.eventId(), NestedExceptionUtils.getMostSpecificCause(e).getClass().getName());
        }
    }
}
