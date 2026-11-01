package com.bbcc.kidly.boundedcontext.delivery.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 판매자 발송 전 부분 취소(#655) 스위치. 기본값(false)은 order.yml에 있다. 받는 쪽(결제 부분 환불 · 상품 재고 복구)이 준비되기
 * 전에는 꺼 두고, 꺼져 있으면 API가 거절하고(409) 이벤트도 나가지 않는다. 값이 없어도 꺼짐이다.
 */
@ConfigurationProperties("kidly.order.partial-cancel")
public record PartialCancelProperties(boolean enabled) {
}
