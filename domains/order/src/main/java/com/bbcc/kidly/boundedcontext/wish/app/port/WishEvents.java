package com.bbcc.kidly.boundedcontext.wish.app.port;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 조르기 이벤트(wish.*, #702)를 주문 이벤트와 같은 길로 낸다: Spring 이벤트 + 같은 트랜잭션의 주문 아웃박스(kidly.order.events).
 * 구현은 out의 주문 컨텍스트 어댑터이고 부르는 쪽 트랜잭션에 합류만 한다. 업무가 롤백되면 이벤트도 남지 않는다.
 */
public interface WishEvents {

    void publish(DomainEvent event);
}
