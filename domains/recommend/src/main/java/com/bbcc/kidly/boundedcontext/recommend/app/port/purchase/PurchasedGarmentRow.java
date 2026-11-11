package com.bbcc.kidly.boundedcontext.recommend.app.port.purchase;

import java.time.LocalDateTime;

/**
 * 자녀가 구매확정한 옷 하나의 구매 기록이다. 상품 이름 같은 상품 정보는 상품 복사본에서 따로 읽는다.
 */
public record PurchasedGarmentRow(Long orderItemId, Long productId, String size, LocalDateTime confirmedAt) {
}
