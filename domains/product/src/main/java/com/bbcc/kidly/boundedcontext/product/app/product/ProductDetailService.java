package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductDetailResult;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.StockDisplay;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductInfoValue;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionValue;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.SizeSpec;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSellerRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryMeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductDetailService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductReplicaSellerRepository replicaSellerRepository;

    @Transactional(readOnly = true)
    public ProductDetailResult findDetail(Long productId) {
        var product = productRepository.getById(productId);
        if (!product.isVisibleToBuyer()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        var category = categoryRepository.getById(product.getCategoryId());
        ProductType type = category.getProductType();
        List<ProductOption> visibleOptions = visibleOptions(product.getOptions());
        List<ProductDetailResult.Option> options = toOptions(visibleOptions);
        return new ProductDetailResult(
            product.getId(),
            product.getName(),
            product.getBrand(),
            findBusinessName(product.getSellerId()),
            product.getCategoryId(),
            product.getDescription(),
            product.getStatus().name(),
            product.isSizeFree(),
            minPrice(options),
            product.getThumbnailUrl(),
            product.getImageUrls(),
            toOptionAttributes(visibleOptions, type),
            options,
            toProductInfo(product.getInfoValues(), type),
            toSizeSpecs(product.getSizeSpecs()),
            toMeasurements(category));
    }

    private String findBusinessName(Long sellerId) {
        return replicaSellerRepository.findById(sellerId).map(ProductReplicaSeller::getBusinessName).orElse(null);
    }

    private List<ProductOption> visibleOptions(List<ProductOption> options) {
        return options.stream().filter(option -> !option.isArchived()).toList();
    }

    private Long minPrice(List<ProductDetailResult.Option> options) {
        return options.stream().map(ProductDetailResult.Option::price).min(Long::compare).orElse(null);
    }

    private List<ProductDetailResult.OptionAttribute> toOptionAttributes(
        List<ProductOption> options, ProductType type) {
        var usedValues = new HashMap<Long, Map<Long, AttributeValue>>();
        for (ProductOption option : options) {
            for (ProductOptionValue optionValue : option.getValues()) {
                AttributeValue value = optionValue.getAttributeValue();
                usedValues.computeIfAbsent(optionValue.getAttribute().getId(), id -> new TreeMap<>())
                    .put(value.getId(), value);
            }
        }
        var results = new ArrayList<ProductDetailResult.OptionAttribute>();
        for (ProductTypeAttribute link : type.getAttributes()) {
            if (link.getRole() != AttributeRole.OPTION) {
                continue;
            }
            Attribute attribute = link.getAttribute();
            var values = new ArrayList<ProductDetailResult.OptionValue>();
            for (AttributeValue value : usedValues.getOrDefault(attribute.getId(), Map.of()).values()) {
                values.add(new ProductDetailResult.OptionValue(value.getId(), value.getName(), value.getColorCode()));
            }
            results.add(new ProductDetailResult.OptionAttribute(
                attribute.getId(), attribute.getCode(), attribute.getName(), link.isSizeRole(), values));
        }
        return results;
    }

    private List<ProductDetailResult.Option> toOptions(List<ProductOption> options) {
        List<Long> optionIds = options.stream().map(ProductOption::getId).toList();
        Map<Long, Integer> availableQtyByOptionId = inventoryRepository.findAvailableQtyByOptionId(optionIds);
        var results = new ArrayList<ProductDetailResult.Option>();
        for (ProductOption option : options) {
            int availableQty = availableQtyByOptionId.getOrDefault(option.getId(), 0);
            StockDisplay stock = StockDisplay.of(option, availableQty);
            var valueIds = new ArrayList<Long>();
            for (ProductOptionValue optionValue : option.getValues()) {
                valueIds.add(optionValue.getAttributeValue().getId());
            }
            results.add(new ProductDetailResult.Option(
                option.getId(),
                option.getName(),
                valueIds,
                option.getPrice(),
                stock.purchasable(),
                stock.status().name(),
                stock.quantity()));
        }
        return results;
    }

    private List<ProductDetailResult.Info> toProductInfo(List<ProductInfoValue> infoValues, ProductType type) {
        var results = new ArrayList<ProductDetailResult.Info>();
        for (ProductTypeAttribute link : type.getAttributes()) {
            if (link.getRole() != AttributeRole.INFO) {
                continue;
            }
            Attribute attribute = link.getAttribute();
            results.add(new ProductDetailResult.Info(
                attribute.getCode(), attribute.getName(), infoText(infoValues, attribute)));
        }
        return results;
    }

    private String infoText(List<ProductInfoValue> infoValues, Attribute attribute) {
        var texts = new ArrayList<String>();
        for (ProductInfoValue infoValue : infoValues) {
            if (!infoValue.getAttribute().getId().equals(attribute.getId())) {
                continue;
            }
            if (infoValue.getAttributeValue() != null) {
                texts.add(infoValue.getAttributeValue().getName());
                continue;
            }
            texts.add(infoValue.getTextValue());
        }
        if (texts.isEmpty()) {
            return null;
        }
        return String.join(", ", texts);
    }

    private List<ProductDetailResult.SizeSpec> toSizeSpecs(List<SizeSpec> sizeSpecs) {
        var results = new ArrayList<ProductDetailResult.SizeSpec>();
        for (SizeSpec spec : sizeSpecs) {
            results.add(new ProductDetailResult.SizeSpec(
                spec.getSizeValue().getName(), spec.getMinHeightCm(), spec.getMaxHeightCm(), spec.getMeasurements()));
        }
        return results;
    }

    private List<ProductDetailResult.Measurement> toMeasurements(Category category) {
        var results = new ArrayList<ProductDetailResult.Measurement>();
        for (CategoryMeasurementItem link : category.getMeasurementItems()) {
            MeasurementItem item = link.getMeasurementItem();
            results.add(new ProductDetailResult.Measurement(item.getCode(), item.getName(), item.getUnit()));
        }
        return results;
    }
}
