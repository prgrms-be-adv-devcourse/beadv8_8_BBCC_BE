package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

final class SizeSpecFixture {

    private SizeSpecFixture() {
    }

    // API 명세 3.2 예시의 상의 110 실측이다 (상품 규칙 7절 초안 값, cm)
    static Map<String, BigDecimal> top110Measurements() {
        return Map.of(
            "TOTAL_LENGTH", new BigDecimal("42"),
            "SHOULDER_WIDTH", new BigDecimal("30"),
            "CHEST_WIDTH", new BigDecimal("34"),
            "SLEEVE_LENGTH", new BigDecimal("38"));
    }

    // API 명세 3.2 예시의 상의 120 실측이다 (상품 규칙 7절 초안 값, cm)
    static Map<String, BigDecimal> top120Measurements() {
        return Map.of(
            "TOTAL_LENGTH", new BigDecimal("45"),
            "SHOULDER_WIDTH", new BigDecimal("32"),
            "CHEST_WIDTH", new BigDecimal("36"),
            "SLEEVE_LENGTH", new BigDecimal("41"));
    }

    static Product.SizeSpecInfo spec(
        AttributeValue sizeValue, Integer minHeightCm, Integer maxHeightCm, Map<String, BigDecimal> measurements) {
        return new Product.SizeSpecInfo(sizeValue, minHeightCm, maxHeightCm, measurements);
    }

    // 상품 A의 판매자가 실측을 넣는 요청이다
    static Product.AddSizeSpecsInfo addSizeSpecs(List<Product.SizeSpecInfo> specs) {
        return new Product.AddSizeSpecsInfo(SELLER_ID, specs);
    }

    // 이미 넣은 실측과 이번에 넣으려는 실측이다. existing이 비어 있으면 먼저 넣은 실측이 없다
    record SpecRequests(List<Product.SizeSpecInfo> existing, List<Product.SizeSpecInfo> requested) {
    }
}
