package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 구매자가 보는 상품 상세다.
 *
 * @param status 상품 상태의 이름
 * @param minPrice 옵션 판매가 중 가장 낮은 값. 옵션이 없으면 null이다
 * @param optionAttributes 상품 유형에 연결한 옵션 항목 순서대로 담는다
 * @param options 옵션을 저장한 순서대로 담는다
 * @param productInfo 상품 유형에 연결한 정보 항목 순서대로 담는다
 * @param sizeSpecs 사이즈 값마다 한 건이고, 저장한 순서대로 담는다
 * @param measurementItems 카테고리에 연결한 순서대로 담는다
 */
public record ProductDetailResult(
    Long productId,
    String name,
    String brand,
    Long categoryId,
    String status,
    boolean sizeFree,
    Long minPrice,
    List<String> imageUrls,
    List<OptionAttribute> optionAttributes,
    List<Option> options,
    List<Info> productInfo,
    List<SizeSpec> sizeSpecs,
    List<Measurement> measurementItems) {

    /**
     * @param values 옵션에 실제로 쓴 값만 값 ID 순서로 담는다
     */
    public record OptionAttribute(Long attributeId, String code, String name, List<OptionValue> values) {
    }

    /**
     * @param colorCode 팔레트 값이 아니면 null
     */
    public record OptionValue(Long valueId, String value, String colorCode) {
    }

    /**
     * @param valueIds 옵션 값의 목록 값 ID를 옵션 항목 순서대로 담는다
     * @param stockStatus 재고 상태의 이름
     * @param stockQuantity 남은 수량. 재고 상태가 LOW일 때만 값이 있고, 그 밖에는 null이다
     */
    public record Option(
        Long optionId,
        String optionName,
        List<Long> valueIds,
        long price,
        boolean purchasable,
        String stockStatus,
        Integer stockQuantity) {
    }

    /**
     * @param value 입력하지 않은 항목이면 null이다. 목록 · 다중 선택 값은 ", "로 이어 붙인다
     */
    public record Info(String code, String name, String value) {
    }

    /**
     * @param minHeightCm 권장 키가 비어 있으면 null
     * @param maxHeightCm 권장 키가 비어 있으면 null
     * @param measurements 키는 실측 항목 코드이고 값은 cm다
     */
    public record SizeSpec(
        String size, Integer minHeightCm, Integer maxHeightCm, Map<String, BigDecimal> measurements) {
    }

    public record Measurement(String code, String name, String unit) {
    }
}
