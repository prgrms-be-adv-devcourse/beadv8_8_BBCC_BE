package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 판매자가 상품을 등록할 때 보내는 값이다. JSON의 문자열 ID("123")도 Long으로 받는다.
 *
 * @param categoryId 상품을 등록할 말단 카테고리
 * @param attributes 상품 정보 항목에 입력한 값
 * @param prices 가격 구분 묶음마다 하나씩이다. 가격 구분 항목이 없는 유형은 valueIds가 빈 목록인 하나만 보낸다
 * @param sizeSpecs 사이즈 값마다 하나씩이다. 사이즈가 없는 상품은 빈 목록이다
 */
public record ProductRegisterRequest(
    String name,
    String brand,
    Long categoryId,
    String description,
    String thumbnailUrl,
    List<String> imageUrls,
    List<AttributeInput> attributes,
    List<OptionInput> options,
    List<PriceInput> prices,
    List<SizeSpecInput> sizeSpecs) {

    /**
     * @param valueIds 목록 항목에서 고른 값. 직접 입력 항목이면 비워 둔다
     * @param text 직접 입력 항목의 값. 목록 항목이면 null이다
     */
    public record AttributeInput(Long attributeId, List<Long> valueIds, String text) {
    }

    /**
     * @param valueIds 옵션 항목마다 고른 값 하나씩이다
     * @param skuCode 없으면 null
     */
    public record OptionInput(List<Long> valueIds, int initialQuantity, String skuCode) {
    }

    /**
     * @param valueIds 이 묶음의 가격 구분 항목 값
     */
    public record PriceInput(List<Long> valueIds, Long price) {
    }

    /**
     * @param measurements 키는 실측 항목 코드이고 값은 cm이다
     */
    public record SizeSpecInput(
        Long sizeValueId, Integer minHeightCm, Integer maxHeightCm, Map<String, BigDecimal> measurements) {
    }
}
