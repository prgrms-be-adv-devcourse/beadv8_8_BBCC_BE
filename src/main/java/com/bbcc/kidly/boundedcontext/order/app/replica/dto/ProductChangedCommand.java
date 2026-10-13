package com.bbcc.kidly.boundedcontext.order.app.replica.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 변경을 복사본에 반영하는 입력. 이벤트에서 주문에 필요한 값만 옮긴다.
 *
 * @param productStatus 판매 상태 이름. 모르는 값이면 구매 불가
 * @param options 이벤트에 담긴 옵션 전체. 여기 없는 기존 옵션은 품절로 바꾼다
 */
public record ProductChangedCommand(
    Long productId,
    Long sellerId,
    String productName,
    Long categoryId,
    String productStatus,
    List<Option> options,
    LocalDateTime occurredAt) {

    public record Option(Long optionId, String size, long unitPrice, boolean soldOut) {
    }
}
