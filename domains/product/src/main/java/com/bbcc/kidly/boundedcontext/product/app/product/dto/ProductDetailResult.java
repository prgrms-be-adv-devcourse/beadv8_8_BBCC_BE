package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductDetailResult(
    Long productId,
    String name,
    String brand,
    String businessName,
    Long categoryId,
    String description,
    String status,
    boolean sizeFree,
    Long minPrice,
    String thumbnailUrl,
    List<String> imageUrls,
    List<OptionAttribute> optionAttributes,
    List<Option> options,
    List<Info> productInfo,
    List<SizeSpec> sizeSpecs,
    List<Measurement> measurementItems) {

    public record OptionAttribute(
        Long attributeId, String code, String name, boolean sizeRole, List<OptionValue> values) {
    }

    public record OptionValue(Long valueId, String value, String colorCode) {
    }

    public record Option(
        Long optionId,
        String optionName,
        List<Long> valueIds,
        long price,
        boolean purchasable,
        String stockStatus,
        Integer stockQuantity) {
    }

    public record Info(String code, String name, String value) {
    }

    public record SizeSpec(
        String size, Integer minHeightCm, Integer maxHeightCm, Map<String, BigDecimal> measurements) {
    }

    public record Measurement(String code, String name, String unit) {
    }
}
