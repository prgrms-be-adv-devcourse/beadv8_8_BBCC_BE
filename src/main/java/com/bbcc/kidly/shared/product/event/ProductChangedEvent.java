package com.bbcc.kidly.shared.product.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 상품의 판매가 시작됐거나 옵션의 품절이 바뀌었다(보완: 상품 수정 · 판매 중지 때도). 주문 · 추천 도메인이 상품 복사본을 갱신한다.
 * 바뀐 뒤의 전체 값을 담으므로 받는 쪽은 덮어쓰기만 한다. 저장된 값보다 {@code occurredAt}이 늦을 때만 반영한다.
 */
public record ProductChangedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param status 판매 상태. 품절은 여기가 아니라 옵션의 {@code soldOut}으로 보낸다
     * @param thumbnailUrl 썸네일 URL. 등록하지 않았으면 null
     * @param createdAt 상품 등록 시각. 판매 시작 시각이 아니다
     * @param sizeFree true면 사이즈 무관 상품이다. 옵션의 권장 키는 null이다
     * @param options 그 상품의 모든 옵션
     */
    public record Data(
            Long productId,
            Long sellerId,
            String name,
            Long categoryId,
            ProductStatus status,
            String thumbnailUrl,
            LocalDateTime createdAt,
            boolean sizeFree,
            List<Option> options) {
    }

    /**
     * @param size 사이즈. 문자열이다(예: "110")
     * @param color 색상. 쓰지 않는 도메인은 읽지 않아도 된다
     * @param material 소재. 쓰지 않는 도메인은 읽지 않아도 된다
     * @param price 판매가(원)
     * @param minHeightCm 권장 키 하한(cm, 포함). 사이즈 무관 상품이면 null
     * @param maxHeightCm 권장 키 상한(cm, 포함). 사이즈 무관 상품이면 null
     * @param soldOut 판매 가능 수량이 0이면 true. 판매 상태와는 별개다
     */
    public record Option(
            Long optionId,
            String size,
            String color,
            String material,
            long price,
            Integer minHeightCm,
            Integer maxHeightCm,
            boolean soldOut) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.productId());
    }
}
