package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductDetailResult;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.StockDisplay;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductInfoValue;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionValue;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.SizeSpec;
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
import com.bbcc.kidly.shared.product.domain.ProductStatus;
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

    // 구매자가 보는 상품 상세를 돌려준다. 엔티티는 트랜잭션 안에서 지연 로딩으로 읽어 결과로 모두 바꾼다
    @Transactional(readOnly = true)
    public ProductDetailResult findDetail(Long productId) {
        Product product = productRepository.getById(productId);
        // 판매를 시작하기 전 상품은 구매자에게 보이지 않으므로 없는 상품과 같이 응답한다
        if (product.getStatus() == ProductStatus.SALE_PENDING) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        Category category = categoryRepository.getById(product.getCategoryId());
        ProductType type = category.getProductType();
        List<ProductOption> visibleOptions = visibleOptions(product.getOptions());
        List<ProductDetailResult.Option> options = toOptions(visibleOptions);
        return new ProductDetailResult(
            product.getId(),
            product.getName(),
            product.getBrand(),
            product.getCategoryId(),
            product.getDescription(),
            product.getStatus().name(),
            product.isSizeFree(),
            minPrice(options),
            product.getImageUrls(),
            toOptionAttributes(visibleOptions, type),
            options,
            toProductInfo(product.getInfoValues(), type),
            toSizeSpecs(product.getSizeSpecs()),
            toMeasurements(category));
    }

    // 보관 옵션은 상세에 보이지 않으므로 옵션 목록과 옵션 항목의 값에서 뺀다
    private List<ProductOption> visibleOptions(List<ProductOption> options) {
        List<ProductOption> visible = new ArrayList<>();
        for (ProductOption option : options) {
            if (!option.isArchived()) {
                visible.add(option);
            }
        }
        return visible;
    }

    // 옵션이 없으면 null이다
    private Long minPrice(List<ProductDetailResult.Option> options) {
        Long min = null;
        for (ProductDetailResult.Option option : options) {
            if (min == null || option.price() < min) {
                min = option.price();
            }
        }
        return min;
    }

    // 항목은 유형에 연결한 순서이고, 값은 옵션에 실제로 쓴 값만 값 ID 순서로 중복 없이 담는다
    private List<ProductDetailResult.OptionAttribute> toOptionAttributes(
        List<ProductOption> options, ProductType type) {
        Map<Long, Map<Long, AttributeValue>> usedValues = new HashMap<>();
        for (ProductOption option : options) {
            for (ProductOptionValue optionValue : option.getValues()) {
                AttributeValue value = optionValue.getAttributeValue();
                usedValues.computeIfAbsent(optionValue.getAttribute().getId(), id -> new TreeMap<>())
                    .put(value.getId(), value);
            }
        }
        List<ProductDetailResult.OptionAttribute> results = new ArrayList<>();
        for (ProductTypeAttribute link : type.getAttributes()) {
            if (link.getRole() != AttributeRole.OPTION) {
                continue;
            }
            Attribute attribute = link.getAttribute();
            List<ProductDetailResult.OptionValue> values = new ArrayList<>();
            for (AttributeValue value : usedValues.getOrDefault(attribute.getId(), Map.of()).values()) {
                values.add(new ProductDetailResult.OptionValue(value.getId(), value.getName(), value.getColorCode()));
            }
            results.add(new ProductDetailResult.OptionAttribute(
                attribute.getId(), attribute.getCode(), attribute.getName(), values));
        }
        return results;
    }

    private List<ProductDetailResult.Option> toOptions(List<ProductOption> options) {
        List<Long> optionIds = new ArrayList<>();
        for (ProductOption option : options) {
            optionIds.add(option.getId());
        }
        Map<Long, Integer> availableQtyByOptionId = inventoryRepository.findAvailableQtyByOptionId(optionIds);
        List<ProductDetailResult.Option> results = new ArrayList<>();
        for (ProductOption option : options) {
            // 재고 행이 없는 옵션은 판매 가능 수량 0으로 본다
            int availableQty = availableQtyByOptionId.getOrDefault(option.getId(), 0);
            StockDisplay stock = StockDisplay.of(option, availableQty);
            List<Long> valueIds = new ArrayList<>();
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

    // 정보 항목은 유형에 연결한 순서이고, 입력한 값이 없으면 null이다
    private List<ProductDetailResult.Info> toProductInfo(List<ProductInfoValue> infoValues, ProductType type) {
        List<ProductDetailResult.Info> results = new ArrayList<>();
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

    // 목록 값은 저장된 순서대로 이름을 ", "로 잇고, 직접 입력 값은 글을 그대로 쓴다. 입력한 값이 없으면 null이다
    private String infoText(List<ProductInfoValue> infoValues, Attribute attribute) {
        List<String> texts = new ArrayList<>();
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
        List<ProductDetailResult.SizeSpec> results = new ArrayList<>();
        for (SizeSpec spec : sizeSpecs) {
            results.add(new ProductDetailResult.SizeSpec(
                spec.getSizeValue().getName(), spec.getMinHeightCm(), spec.getMaxHeightCm(), spec.getMeasurements()));
        }
        return results;
    }

    private List<ProductDetailResult.Measurement> toMeasurements(Category category) {
        List<ProductDetailResult.Measurement> results = new ArrayList<>();
        for (CategoryMeasurementItem link : category.getMeasurementItems()) {
            MeasurementItem item = link.getMeasurementItem();
            results.add(new ProductDetailResult.Measurement(item.getCode(), item.getName(), item.getUnit()));
        }
        return results;
    }
}
