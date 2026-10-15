package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 상품 정보 변경됨에 담을 값을 상품 애그리거트와 옵션별 판매 가능 수량으로 만든다.
 * 판매 시작과 품절 변경이 함께 쓴다.
 */
public final class ProductChangedData {

    private ProductChangedData() {
    }

    // 옵션은 상품에 저장된 순서대로 담는다. availableQtyByOptionId에 없는 옵션은 재고 행이 없어 살 수 없으므로 품절로 본다
    public static ProductChangedEvent.Data of(
        Product product, ProductType productType, Map<Long, Integer> availableQtyByOptionId) {
        List<ProductChangedEvent.Option> options = new ArrayList<>();
        for (ProductOption option : product.getOptions()) {
            // 보관한 옵션은 다시 팔 수 없어 받는 쪽에 보내지 않는다 (SALE-02)
            if (option.isArchived()) {
                continue;
            }
            options.add(toOption(product, option, productType, availableQtyByOptionId));
        }
        return new ProductChangedEvent.Data(
            product.getId(), product.getSellerId(), product.getName(), product.getCategoryId(),
            product.getStatus(), product.getThumbnailUrl(), product.getCreatedAt(), product.isSizeFree(), options);
    }

    private static ProductChangedEvent.Option toOption(
        Product product, ProductOption option, ProductType productType, Map<Long, Integer> availableQtyByOptionId) {
        AttributeValue sizeValue = findSizeValue(option, productType);
        String size = null;
        Integer minHeightCm = null;
        Integer maxHeightCm = null;
        if (sizeValue != null) {
            size = sizeValue.getName();
            SizeSpec spec = findSizeSpec(product, sizeValue);
            if (spec != null) {
                minHeightCm = spec.getMinHeightCm();
                maxHeightCm = spec.getMaxHeightCm();
            }
        }
        boolean soldOut = availableQtyByOptionId.getOrDefault(option.getId(), 0) <= 0;
        return new ProductChangedEvent.Option(
            option.getId(), option.getName(), size, option.getPrice(), minHeightCm, maxHeightCm, soldOut,
            toSaleStatus(option.getSaleStatus()), toOptionValueEntries(option));
    }

    // 옵션 값 조합을 항목 코드 · 값 ID · 값으로 옮긴다. 옵션 값은 모두 목록 값이라 값 ID가 있다
    private static List<ProductChangedEvent.AttributeEntry> toOptionValueEntries(ProductOption option) {
        List<ProductChangedEvent.AttributeEntry> entries = new ArrayList<>();
        for (ProductOptionValue value : option.getValues()) {
            AttributeValue attributeValue = value.getAttributeValue();
            entries.add(new ProductChangedEvent.AttributeEntry(
                value.getAttribute().getCode(), attributeValue.getId(), attributeValue.getName()));
        }
        return entries;
    }

    // 상품 안의 옵션 상태를 이벤트의 판매 상태로 바꾼다. 상품 안에 상태가 늘면 컴파일 오류로 이곳을 고치게 한다
    private static OptionSaleStatus toSaleStatus(ProductOptionStatus status) {
        return switch (status) {
            case ON_SALE -> OptionSaleStatus.ON_SALE;
            case STOPPED -> OptionSaleStatus.STOPPED;
            case ARCHIVED -> throw new IllegalArgumentException("보관한 옵션은 상품 정보 변경됨에 담지 않습니다");
        };
    }

    // 옵션 값 중 유형이 사이즈 역할로 정한 값을 찾는다. 없으면 null이다
    private static AttributeValue findSizeValue(ProductOption option, ProductType productType) {
        for (ProductOptionValue value : option.getValues()) {
            if (productType.isSizeValue(value.getAttributeValue())) {
                return value.getAttributeValue();
            }
        }
        return null;
    }

    // 같은 영속성 컨텍스트에서는 같은 사이즈 값이 같은 객체이므로 ==로 비교한다. 실측이 없으면 null이다
    private static SizeSpec findSizeSpec(Product product, AttributeValue sizeValue) {
        for (SizeSpec spec : product.getSizeSpecs()) {
            if (spec.getSizeValue() == sizeValue) {
                return spec;
            }
        }
        return null;
    }
}
