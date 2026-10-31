package com.bbcc.kidly.boundedcontext.delivery.api;

/**
 * 배송 상태를 바꾼 주체(#617). 주문 상태 이력의 주체 종류 이름(BUYER · SELLER · ADMIN · SYSTEM · EVENT) · 회원 ID · 요청 · 작업 · 이벤트 이름.
 *
 * @param id 회원 ID. 시스템 · 이벤트는 null
 */
public record DeliveryActor(String type, Long id, String source) {
}
