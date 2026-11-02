package com.bbcc.kidly.boundedcontext.review.app.port.in;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 변경을 복사본에 반영한다. 받는 쪽(in/event)이 이벤트를 이 값으로 바꿔 부른다.
 * 같은 값이 다시 와도 결과는 1회분이다: 저장된 값보다 늦게 발생한 것만 반영한다.
 */
public interface ProductReplicaRecorder {

    void record(ProductChanged changed);

    /**
     * @param productOnSale 상품이 판매 중인지
     * @param options 옵션 판매 상태 · 품절 여부. 없으면 빈 목록
     */
    record ProductChanged(
        Long productId,
        String name,
        String thumbnailUrl,
        boolean productOnSale,
        List<Option> options,
        LocalDateTime occurredAt) {
    }

    /**
     * @param onSale 옵션이 판매 중인지
     * @param inStock 재고가 있는지
     */
    record Option(boolean onSale, boolean inStock) {
    }
}
