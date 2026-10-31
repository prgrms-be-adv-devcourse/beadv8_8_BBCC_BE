package com.bbcc.kidly.boundedcontext.delivery.api;

/**
 * 배송 상태가 바뀐 결과(#617).
 *
 * @param from 바뀌기 전 상태 이름
 * @param delivery 바뀐 뒤 배송
 */
public record DeliveryChange(String from, DeliveryView delivery) {
}
