package com.bbcc.kidly.boundedcontext.notification.app.dto;

import java.util.List;

/**
 * 자녀 알림함에 넣을 조르기 거절.
 *
 * @param childProfileId 자녀 프로필 ID. 활성 계정을 찾아 받는 사람으로 쓴다
 * @param productIds 조른 상품 ID(조른 순서). 주문이 상품 정보를 받아 두지 않았으면 null이 섞일 수 있다
 * @param reason 거절 사유 이름(TOO_EXPENSIVE · ALREADY_HAVE · NEXT_TIME · OTHER). 모르는 값이면 OTHER처럼 쓴다
 */
public record WishRejectedCommand(Long wishRequestId, Long childProfileId, List<Long> productIds, String reason) {
}
