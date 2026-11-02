package com.bbcc.kidly.boundedcontext.wish.out.order;

import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderEventIssuer;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishEvents;
import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 조르기 이벤트를 주문 컨텍스트의 발행 포트로 낸다(#702, 장바구니 #654와 같은 길). 같은 앱 안의 동기 호출이라 부르는 쪽 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishEventsAdapter implements WishEvents {

    private final OrderEventIssuer orderEventIssuer;

    @Override
    public void publish(DomainEvent event) {
        orderEventIssuer.issue(event);
    }
}
