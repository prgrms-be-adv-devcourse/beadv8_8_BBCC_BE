package com.bbcc.kidly.boundedcontext.product.in.product.dto;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductDetailResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @param minPrice 옵션이 없으면 null
 * @param optionAttributes 상품 유형에 연결한 옵션 항목 순서대로 담는다
 * @param productInfo 상품 유형에 연결한 정보 항목 순서대로 담는다
 * @param measurementItems 카테고리에 연결한 순서대로 담는다
 */
public record ProductDetailResponse(
    String productId,
    String name,
    String brand,
    String categoryId,
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
    public record OptionAttribute(String attributeId, String code, String name, List<OptionValue> values) {

        static OptionAttribute from(ProductDetailResult.OptionAttribute result) {
            List<OptionValue> values = new ArrayList<>();
            for (ProductDetailResult.OptionValue value : result.values()) {
                values.add(new OptionValue(String.valueOf(value.valueId()), value.value(), value.colorCode()));
            }
            return new OptionAttribute(String.valueOf(result.attributeId()), result.code(), result.name(), values);
        }
    }

    /**
     * @param colorCode 팔레트 값이 아니면 null
     */
    public record OptionValue(String valueId, String value, String colorCode) {
    }

    /**
     * @param stockStatus AVAILABLE, LOW, SOLD_OUT 중 하나
     * @param stockQuantity 남은 수량. stockStatus가 LOW일 때만 값이 있고, 그 밖에는 null이다
     */
    public record Option(
        String optionId,
        String optionName,
        List<String> valueIds,
        long price,
        boolean purchasable,
        String stockStatus,
        Integer stockQuantity) {

        static Option from(ProductDetailResult.Option result) {
            List<String> valueIds = new ArrayList<>();
            for (Long valueId : result.valueIds()) {
                valueIds.add(String.valueOf(valueId));
            }
            return new Option(
                String.valueOf(result.optionId()),
                result.optionName(),
                valueIds,
                result.price(),
                result.purchasable(),
                result.stockStatus(),
                result.stockQuantity());
        }
    }

    /**
     * @param value 입력하지 않은 항목이면 null이다
     */
    public record Info(String code, String name, String value) {
    }

    /**
     * @param measurements 키는 실측 항목 코드이고 값은 cm다
     */
    public record SizeSpec(
        String size, Integer minHeightCm, Integer maxHeightCm, Map<String, BigDecimal> measurements) {
    }

    public record Measurement(String code, String name, String unit) {
    }

    public static ProductDetailResponse from(ProductDetailResult result) {
        List<OptionAttribute> optionAttributes = new ArrayList<>();
        for (ProductDetailResult.OptionAttribute attribute : result.optionAttributes()) {
            optionAttributes.add(OptionAttribute.from(attribute));
        }
        List<Option> options = new ArrayList<>();
        for (ProductDetailResult.Option option : result.options()) {
            options.add(Option.from(option));
        }
        List<Info> productInfo = new ArrayList<>();
        for (ProductDetailResult.Info info : result.productInfo()) {
            productInfo.add(new Info(info.code(), info.name(), info.value()));
        }
        List<SizeSpec> sizeSpecs = new ArrayList<>();
        for (ProductDetailResult.SizeSpec spec : result.sizeSpecs()) {
            sizeSpecs.add(new SizeSpec(spec.size(), spec.minHeightCm(), spec.maxHeightCm(), spec.measurements()));
        }
        List<Measurement> measurementItems = new ArrayList<>();
        for (ProductDetailResult.Measurement item : result.measurementItems()) {
            measurementItems.add(new Measurement(item.code(), item.name(), item.unit()));
        }
        return new ProductDetailResponse(
            String.valueOf(result.productId()),
            result.name(),
            result.brand(),
            String.valueOf(result.categoryId()),
            result.status(),
            result.sizeFree(),
            result.minPrice(),
            result.imageUrls(),
            optionAttributes,
            options,
            productInfo,
            sizeSpecs,
            measurementItems);
    }
}
