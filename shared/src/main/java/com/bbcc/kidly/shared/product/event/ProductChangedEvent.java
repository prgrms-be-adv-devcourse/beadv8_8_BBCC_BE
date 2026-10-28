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
 * 상품 자신의 검색용 표도 이 이벤트로 다시 만든다. 옵션 값과 상품 정보 값은 검색용 표가 항목 코드로 상품을 거르는 데 쓴다.
 */
public record ProductChangedEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    /**
     * @param status 판매 상태. 품절은 여기가 아니라 옵션의 {@code soldOut}으로 보낸다
     * @param thumbnailUrl 썸네일 URL. 등록하지 않았으면 null
     * @param createdAt 상품 등록 시각. 판매 시작 시각이 아니다
     * @param sizeFree true면 사이즈 무관 상품이다. 추천은 권장 키가 아니라 이 값으로 사이즈 무관을 판단한다
     * @param options 그 상품의 옵션. 보관한 옵션은 넣지 않는다
     * @param infoValues 상품 정보 값이다. 다중 선택 목록 항목은 같은 {@code code}로 여러 개를 담는다
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
            List<Option> options,
            List<AttributeEntry> infoValues) {

        /**
         * 이벤트를 만드는 주문 · 추천 테스트 코드가 바뀌지 않게 하려고, 상품 정보 값 없이 만드는 생성자를 둔다.
         * 상품이 이벤트를 보낼 때는 이 생성자를 쓰지 않는다. 상품 정보 값이 비면 검색용 표에서 그 값으로 거를 수 없게 된다.
         */
        public Data(
                Long productId,
                Long sellerId,
                String name,
                Long categoryId,
                ProductStatus status,
                String thumbnailUrl,
                LocalDateTime createdAt,
                boolean sizeFree,
                List<Option> options) {
            this(productId, sellerId, name, categoryId, status, thumbnailUrl, createdAt, sizeFree, options, List.of());
        }
    }

    /**
     * @param optionName 화면과 주문상품에 보여 줄 옵션 이름이다(예: "색상: 네이비 / 사이즈: 110")
     * @param size 사이즈. 문자열이다(예: "110")
     * @param price 판매가(원)
     * @param minHeightCm 권장 키 하한(cm, 포함). 입력하지 않았으면 null. 사이즈 무관 상품은 넣지 않을 수 있다
     * @param maxHeightCm 권장 키 상한(cm, 포함). 입력하지 않았으면 null. 사이즈 무관 상품은 넣지 않을 수 있다
     * @param soldOut 판매 가능 수량이 0이면 true. 상품 · 옵션 판매 상태와는 별개다
     * @param saleStatus 옵션 판매 상태다. 품절은 여기가 아니라 {@code soldOut}으로 보낸다.
     *        상품 판매 상태({@code status})와 따로 본다. 상품이 판매 중이 아니면 옵션이 ON_SALE이어도 팔 수 없다
     * @param values 그 옵션의 옵션 값 조합이다(예: 색상 네이비, 사이즈 110)
     */
    public record Option(
            Long optionId,
            String optionName,
            String size,
            long price,
            Integer minHeightCm,
            Integer maxHeightCm,
            boolean soldOut,
            OptionSaleStatus saleStatus,
            List<AttributeEntry> values) {

        /**
         * 이벤트를 만드는 주문 · 추천 테스트 코드가 바뀌지 않게 하려고, 옵션 값 없이 만드는 생성자를 둔다.
         * 상품이 이벤트를 보낼 때는 이 생성자를 쓰지 않는다. 옵션 값이 비면 검색용 표에서 그 값으로 거를 수 없게 된다.
         */
        public Option(
                Long optionId,
                String optionName,
                String size,
                long price,
                Integer minHeightCm,
                Integer maxHeightCm,
                boolean soldOut,
                OptionSaleStatus saleStatus) {
            this(optionId, optionName, size, price, minHeightCm, maxHeightCm, soldOut, saleStatus, List.of());
        }
    }

    /**
     * 항목 값 하나다.
     *
     * @param code 항목 코드. 바뀌지 않는 기계용 이름이다(예: "COLOR", "MAIN_MATERIAL")
     * @param valueId 고른 목록 값의 ID. 직접 입력 항목이면 null이다
     * @param value 화면에 보이는 값이다. 목록 항목이면 목록 값 이름, 직접 입력 항목이면 판매자가 입력한 글이다
     */
    public record AttributeEntry(String code, Long valueId, String value) {
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.productId());
    }
}
