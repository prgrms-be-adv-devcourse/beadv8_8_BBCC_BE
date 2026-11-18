package com.bbcc.kidly.boundedcontext.notification.app.dto;

import java.util.List;

/**
 * @param productIds 조른 상품 ID(결제 완료는 일부만 결제해도 조른 상품 전체). 주문이 상품 정보를 받아 두지 않았으면 null이 섞일 수 있다
 */
public record ChildWishCommand(Long wishRequestId, Long childProfileId, List<Long> productIds) {
}
