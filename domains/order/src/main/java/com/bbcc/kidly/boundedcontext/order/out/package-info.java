/**
 * 나가는 연결(#651): {@code persistence}(Spring Data JPA 저장소만), {@code outbox}(이벤트 아웃박스 메시지 · 저장 · 포트 구현),
 * {@code kafka}(Kafka 전송 · 메시지 봉투 · 결제 이벤트 대기량 확인), {@code deadletter}(DLT 보관 행 · 포트 구현),
 * {@code lock}(서버 한 대 실행 DB 잠금), {@code config}(설정값 → OrderPolicy · Kafka 토픽 선언),
 * {@code cart} · {@code delivery}(다른 컨텍스트 어댑터).
 */
package com.bbcc.kidly.boundedcontext.order.out;
