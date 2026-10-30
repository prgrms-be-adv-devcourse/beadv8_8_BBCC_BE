/**
 * 나가는 연결: {@code persistence}(저장소 포트 구현 · 복사본 테이블), {@code event}(이벤트 발행),
 * {@code tracker}(배송 상태 조회), {@code config}(설정값 → OrderPolicy · Kafka 토픽 선언),
 * {@code messaging}(이벤트 아웃박스 저장 · Kafka 전송, #540).
 */
package com.bbcc.kidly.boundedcontext.order.out;
