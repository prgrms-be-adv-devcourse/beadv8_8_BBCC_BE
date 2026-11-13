package com.bbcc.kidly.boundedcontext.order.app.replica.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 변경을 복사본에 반영하는 입력. 이벤트에서 주문에 필요한 값만 옮긴다.
 *
 * @param productStatus 판매 상태 이름. 모르는 값이면 구매 불가
 * @param options 이벤트에 담긴 옵션 전체. 여기 없는 기존 옵션은 품절로 바꾼다
 * @param thumbnailUrl 상품 썸네일 URL(#1328). 없거나 이 칸이 없던 옛 메시지면 null
 */
public record ProductChangedCommand(
    Long productId,
    Long sellerId,
    String productName,
    Long categoryId,
    String productStatus,
    List<Option> options,
    LocalDateTime occurredAt,
    String thumbnailUrl) {

    /**
     * 썸네일 없이(#1328 전 입력 모양). 썸네일은 null.
     */
    public ProductChangedCommand(Long productId, Long sellerId, String productName, Long categoryId,
        String productStatus, List<Option> options, LocalDateTime occurredAt) {
        this(productId, sellerId, productName, categoryId, productStatus, options, occurredAt, null);
    }

    /**
     * @param optionName 옵션 이름
     * @param saleStatus 옵션 판매 상태 이름(ON_SALE · STOPPED). 복사본에 그대로 저장한다
     */
    public record Option(Long optionId, String optionName, String size, long unitPrice, boolean soldOut,
        String saleStatus) {
    }
}
