package com.bbcc.kidly.shared.product.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.domain.CategoryVisibleGender;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
     * @param createdAt 상품 등록 시각. 판매 시작 시각은 {@code saleStartedAt}이다
     * @param sizeFree true면 사이즈 무관 상품이다. 추천은 권장 키가 아니라 이 값으로 사이즈 무관을 판단한다
     * @param options 그 상품의 옵션. 보관한 옵션은 넣지 않는다
     * @param infoValues 상품 정보 값이다. 다중 선택 목록 항목은 같은 {@code code}로 여러 개를 담는다
     * @param brand 브랜드. 등록하지 않았으면 null
     * @param description 상품 설명. 등록하지 않았으면 null
     * @param sizeSpecs 사이즈별 실측이다. 상품의 실측 행마다 하나를 담고, 사이즈 무관 상품의 FREE 행도 담는다.
     *        사이즈 역할 항목이 없는 유형의 상품처럼 실측 행이 없으면 빈 목록이다. 이 칸이 생기기 전에 나간 메시지도 빈 목록으로 읽힌다
     * @param categoryPath 상품 카테고리의 경로다. 대분류부터 상품의 종류 카테고리까지 차례로 담고, 마지막 원소의 id는 {@code categoryId}와 같다.
     *        이 칸이 생기기 전에 나간 메시지는 빈 목록으로 읽힌다
     * @param stoppedByAdmin true면 관리자가 운영 정책에 따라 판매를 중지한 상품이다. 판매자는 다시 판매를 시작할 수 없다.
     *        이 칸이 생기기 전에 나간 메시지는 false로 읽힌다
     * @param saleStartedAt 상품이 처음 판매중이 된 시각(한국 시간)이다. 판매중지 뒤 다시 판매해도 바뀌지 않는다.
     *        이 칸이 생기기 전에 나간 메시지와, 판매를 시작하지 않고 등록 취소한 상품은 null이므로 받는 쪽은 null을 다뤄야 한다
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
            List<AttributeEntry> infoValues,
            String brand,
            String description,
            List<SizeSpec> sizeSpecs,
            List<CategoryPathEntry> categoryPath,
            Boolean stoppedByAdmin,
            LocalDateTime saleStartedAt) {

        // 사이즈별 실측 · 카테고리 경로 칸이 없는 예전 메시지를 읽으면 null이 들어오므로, 받는 쪽이 null을 검사하지 않게 빈 목록으로 바꾼다
        public Data {
            if (sizeSpecs == null) {
                sizeSpecs = List.of();
            }
            if (categoryPath == null) {
                categoryPath = List.of();
            }
            // 칸이 없는 예전 메시지는 null로 읽힌다. boolean이면 역직렬화가 실패하므로 Boolean으로 받아 false로 바꾼다
            if (stoppedByAdmin == null) {
                stoppedByAdmin = false;
            }
        }

        /**
         * 이벤트를 만드는 다른 도메인 테스트 코드가 바뀌지 않게 하려고, 판매 시작 시각 없이 만드는 생성자를 둔다(null).
         * 상품이 이벤트를 보낼 때는 이 생성자를 쓰지 않는다.
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
                List<Option> options,
                List<AttributeEntry> infoValues,
                String brand,
                String description,
                List<SizeSpec> sizeSpecs,
                List<CategoryPathEntry> categoryPath,
                Boolean stoppedByAdmin) {
            this(productId, sellerId, name, categoryId, status, thumbnailUrl, createdAt, sizeFree, options,
                    infoValues, brand, description, sizeSpecs, categoryPath, stoppedByAdmin, null);
        }

        /**
         * 이벤트를 만드는 다른 도메인 테스트 코드가 바뀌지 않게 하려고, 관리자 판매 중지 여부 없이 만드는 생성자를 둔다(false).
         * 상품이 이벤트를 보낼 때는 이 생성자를 쓰지 않는다.
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
                List<Option> options,
                List<AttributeEntry> infoValues,
                String brand,
                String description,
                List<SizeSpec> sizeSpecs,
                List<CategoryPathEntry> categoryPath) {
            this(productId, sellerId, name, categoryId, status, thumbnailUrl, createdAt, sizeFree, options,
                    infoValues, brand, description, sizeSpecs, categoryPath, false);
        }

        /**
         * 이벤트를 만드는 다른 도메인 테스트 코드가 바뀌지 않게 하려고, 카테고리 경로 없이 만드는 생성자를 둔다.
         * 카테고리 경로는 빈 목록이 된다. 상품이 이벤트를 보낼 때 쓰면 경로가 빠지므로 쓰지 않는다.
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
                List<Option> options,
                List<AttributeEntry> infoValues,
                String brand,
                String description,
                List<SizeSpec> sizeSpecs) {
            this(productId, sellerId, name, categoryId, status, thumbnailUrl, createdAt, sizeFree, options,
                    infoValues, brand, description, sizeSpecs, List.of());
        }

        /**
         * 이벤트를 만드는 다른 도메인 테스트 코드가 바뀌지 않게 하려고, 브랜드 · 설명 · 사이즈별 실측 없이 만드는 생성자를 둔다.
         * 브랜드와 설명은 null, 사이즈별 실측과 카테고리 경로는 빈 목록이 된다. 상품이 이벤트를 보낼 때 쓰면 네 값이 빠지므로 쓰지 않는다.
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
                List<Option> options,
                List<AttributeEntry> infoValues) {
            this(productId, sellerId, name, categoryId, status, thumbnailUrl, createdAt, sizeFree, options,
                    infoValues, null, null, List.of());
        }

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
     * 카테고리 경로의 한 단계다.
     *
     * @param id 상품 도메인의 카테고리 ID
     * @param name 카테고리 이름. 카테고리 ID는 환경마다 달라 받는 쪽은 이름으로 대분류와 종류를 알아본다
     * @param visibleGender 이 카테고리를 보여 줄 자녀 성별이다. GIRL이면 남아 자녀에게는 보여 주지 않는다.
     *        이 칸이 생기기 전에 나간 메시지는 ALL로 읽힌다
     */
    public record CategoryPathEntry(Long id, String name, CategoryVisibleGender visibleGender) {

        // 칸이 없는 예전 메시지는 null로 읽히므로, 받는 쪽이 null을 검사하지 않게 ALL로 바꾼다
        public CategoryPathEntry {
            if (visibleGender == null) {
                visibleGender = CategoryVisibleGender.ALL;
            }
        }

        /**
         * 이벤트를 만드는 다른 도메인 코드와 테스트가 바뀌지 않게 하려고, 보일 성별 없이 만드는 생성자를 둔다(ALL).
         */
        public CategoryPathEntry(Long id, String name) {
            this(id, name, CategoryVisibleGender.ALL);
        }
    }

    /**
     * 사이즈 하나의 실측이다. 옵션이 아니라 사이즈 단위로 담는다. 색상만 다른 옵션은 실측이 같으므로 옵션마다 반복하지 않는다.
     *
     * @param size 사이즈. 문자열이다(예: "110"). 사이즈 무관 상품은 "FREE"다
     * @param minHeightCm 권장 키 하한(cm, 포함). 입력하지 않았으면 null
     * @param maxHeightCm 권장 키 상한(cm, 포함). 입력하지 않았으면 null
     * @param measurements 실측 값이다. 키는 실측 항목 코드, 값은 cm다
     */
    public record SizeSpec(
            String size,
            Integer minHeightCm,
            Integer maxHeightCm,
            Map<String, BigDecimal> measurements) {
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
