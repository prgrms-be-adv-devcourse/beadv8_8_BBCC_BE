package com.bbcc.kidly.boundedcontext.delivery.out.order;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryEvents;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderEventIssuer;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 배송 이벤트를 주문 컨텍스트의 발행 포트로 낸다(#900, 장바구니 #654 · 조르기 #702와 같은 길). 같은 앱 안의 동기 호출이라
 * 부르는 쪽 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryEventsAdapter implements DeliveryEvents {

    private final OrderEventIssuer orderEventIssuer;

    @Override
    public void publish(DomainEvent event) {
        orderEventIssuer.issue(event);
    }
}
