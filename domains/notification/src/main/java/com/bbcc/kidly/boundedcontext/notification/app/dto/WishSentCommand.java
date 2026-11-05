package com.bbcc.kidly.boundedcontext.notification.app.dto;

import java.util.List;

/**
 * 보호자 알림함에 넣을 조르기 요청.
 *
 * @param memberId 그 자녀의 보호자 회원 ID
 * @param productIds 조른 상품 ID(조른 순서). 주문이 상품 정보를 받아 두지 않았으면 null이 섞일 수 있다
 */
public record WishSentCommand(Long wishRequestId, Long childId, Long memberId, List<Long> productIds) {
}
