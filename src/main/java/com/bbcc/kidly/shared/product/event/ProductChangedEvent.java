package com.bbcc.kidly.shared.product.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
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
     * @param sizeFree true면 사이즈 무관 상품이다. 추천은 권장 키가 아니라 이 값으로 사이즈 무관을 판단한다
     * @param options 그 상품의 옵션. 보관한 옵션은 넣지 않는다
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
     * @param optionName 화면과 주문상품에 보여 줄 옵션 이름이다(예: "색상: 네이비 / 사이즈: 110")
     * @param size 사이즈. 문자열이다(예: "110")
     * @param price 판매가(원)
     * @param minHeightCm 권장 키 하한(cm, 포함). 입력하지 않았으면 null. 사이즈 무관 상품은 넣지 않을 수 있다
     * @param maxHeightCm 권장 키 상한(cm, 포함). 입력하지 않았으면 null. 사이즈 무관 상품은 넣지 않을 수 있다
     * @param soldOut 판매 가능 수량이 0이면 true. 판매 상태와는 별개다
     * @param saleStatus 옵션 판매 상태다. 품절은 여기가 아니라 {@code soldOut}으로 보낸다
     */
    public record Option(
            Long optionId,
            String optionName,
            String size,
            long price,
            Integer minHeightCm,
            Integer maxHeightCm,
            boolean soldOut,
            OptionSaleStatus saleStatus) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.productId());
    }
}
