package com.bbcc.kidly.boundedcontext.order.app.port.in;

import com.bbcc.kidly.shared.event.DomainEvent;

/**
 * 다른 컨텍스트(장바구니)가 주문 모듈 이벤트를 주문 이벤트와 같은 길로 발행한다(#654): Spring 이벤트 발행 + 같은 트랜잭션의
 * 아웃박스 저장(kidly.order.events). 구현은 주문 app이고 부르는 쪽 트랜잭션에 합류만 한다(트랜잭션이 없으면 실패).
 */
public interface OrderEventIssuer {

    void issue(DomainEvent event);
}
