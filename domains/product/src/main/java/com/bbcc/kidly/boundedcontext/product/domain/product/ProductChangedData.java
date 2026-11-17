package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProductChangedData {

    private ProductChangedData() {
    }

    public static ProductChangedEvent.Data of(
        Product product, Category category, Map<Long, Integer> availableQtyByOptionId) {
        ProductType productType = category.getProductType();
        List<ProductChangedEvent.Option> options = new ArrayList<>();
        for (ProductOption option : product.getOptions()) {
            if (option.isArchived()) {
                continue;
            }
            options.add(toOption(product, option, productType, availableQtyByOptionId));
        }
        return new ProductChangedEvent.Data(
            product.getId(), product.getSellerId(), product.getName(), product.getCategoryId(),
            product.getStatus(), product.getThumbnailUrl(), product.getCreatedAt(), product.isSizeFree(), options,
            toInfoValueEntries(product), product.getBrand(), product.getDescription(), toSizeSpecs(product),
            toCategoryPath(category), product.isStoppedByAdmin(), product.getSaleStartedAt());
    }

    private static List<ProductChangedEvent.CategoryPathEntry> toCategoryPath(Category category) {
        List<ProductChangedEvent.CategoryPathEntry> entries = new ArrayList<>();
        for (Category node : category.path()) {
            entries.add(new ProductChangedEvent.CategoryPathEntry(
                node.getId(), node.getName(), node.getVisibleGender()));
        }
        return entries;
    }

    private static List<ProductChangedEvent.SizeSpec> toSizeSpecs(Product product) {
        List<ProductChangedEvent.SizeSpec> sizeSpecs = new ArrayList<>();
        for (SizeSpec spec : product.getSizeSpecs()) {
            sizeSpecs.add(new ProductChangedEvent.SizeSpec(spec.getSizeValue().getName(), spec.getMinHeightCm(),
                spec.getMaxHeightCm(), Map.copyOf(spec.getMeasurements())));
        }
        return sizeSpecs;
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

    private static List<ProductChangedEvent.AttributeEntry> toOptionValueEntries(ProductOption option) {
        List<ProductChangedEvent.AttributeEntry> entries = new ArrayList<>();
        for (ProductOptionValue value : option.getValues()) {
            AttributeValue attributeValue = value.getAttributeValue();
            entries.add(new ProductChangedEvent.AttributeEntry(
                value.getAttribute().getCode(), attributeValue.getId(), attributeValue.getName()));
        }
        return entries;
    }

    private static List<ProductChangedEvent.AttributeEntry> toInfoValueEntries(Product product) {
        List<ProductChangedEvent.AttributeEntry> entries = new ArrayList<>();
        for (ProductInfoValue infoValue : product.getInfoValues()) {
            AttributeValue attributeValue = infoValue.getAttributeValue();
            if (attributeValue == null) {
                entries.add(new ProductChangedEvent.AttributeEntry(
                    infoValue.getAttribute().getCode(), null, infoValue.getTextValue()));
                continue;
            }
            entries.add(new ProductChangedEvent.AttributeEntry(
                infoValue.getAttribute().getCode(), attributeValue.getId(), attributeValue.getName()));
        }
        return entries;
    }

    private static OptionSaleStatus toSaleStatus(ProductOptionStatus status) {
        return switch (status) {
            case ON_SALE -> OptionSaleStatus.ON_SALE;
            case STOPPED -> OptionSaleStatus.STOPPED;
            case ARCHIVED -> throw new IllegalArgumentException("보관한 옵션은 상품 정보 변경됨에 담지 않습니다");
        };
    }

    private static AttributeValue findSizeValue(ProductOption option, ProductType productType) {
        for (ProductOptionValue value : option.getValues()) {
            if (productType.isSizeValue(value.getAttributeValue())) {
                return value.getAttributeValue();
            }
        }
        return null;
    }

    private static SizeSpec findSizeSpec(Product product, AttributeValue sizeValue) {
        for (SizeSpec spec : product.getSizeSpecs()) {
            if (spec.getSizeValue() == sizeValue) {
                return spec;
            }
        }
        return null;
    }
}
