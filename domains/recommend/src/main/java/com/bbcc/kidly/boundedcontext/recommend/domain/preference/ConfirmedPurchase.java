package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import java.time.LocalDateTime;

/**
 * 선호 계산이 읽는 구매확정 한 건이다. 상품 복사본이 없으면 brand가 null이다.
 */
public record ConfirmedPurchase(Long productId, String brand, LocalDateTime confirmedAt) {
}
