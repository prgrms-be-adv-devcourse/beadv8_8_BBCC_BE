package com.bbcc.kidly.boundedcontext.cart.app.port;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 장바구니 이벤트 발행(#654). 구현은 out의 주문 컨텍스트 어댑터(주문 이벤트와 같은 Spring 이벤트 + 아웃박스)이고,
 * 업무 트랜잭션 안에서 불러야 한다. 업무가 롤백되면 메시지도 남지 않는다.
 */
public interface CartEvents {

    void publish(DomainEvent event);
}
