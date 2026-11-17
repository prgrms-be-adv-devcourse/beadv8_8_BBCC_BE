package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SellerProductDetailResult(
    Long productId, String name, String status, Long version,
    String brand, Long categoryId, String description, String thumbnailUrl, List<String> imageUrls,
    List<Attribute> attributes, List<Option> options, List<Price> prices, List<SizeSpec> sizeSpecs) {

    public record Attribute(Long attributeId, List<Long> valueIds, String text) {
    }

    public record Option(
        Long optionId, String optionName, List<Long> valueIds, long price,
        int availableQuantity, String saleStatus, String skuCode) {
    }

    public record Price(List<Long> valueIds, long price) {
    }

    public record SizeSpec(
        Long sizeValueId, Integer minHeightCm, Integer maxHeightCm, Map<String, BigDecimal> measurements) {
    }
}
