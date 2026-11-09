package com.bbcc.kidly.boundedcontext.notification.app.dto;

import java.util.List;

/**
 * 자녀 알림함에 넣을 조르기 결제 완료 · 만료.
 *
 * @param childProfileId 자녀 프로필 ID. 활성 계정을 찾아 받는 사람으로 쓴다
 * @param productIds 조른 상품 ID(결제 완료는 일부만 결제해도 조른 상품 전체). 주문이 상품 정보를 받아 두지 않았으면 null이 섞일 수 있다
 */
public record ChildWishCommand(Long wishRequestId, Long childProfileId, List<Long> productIds) {
}
