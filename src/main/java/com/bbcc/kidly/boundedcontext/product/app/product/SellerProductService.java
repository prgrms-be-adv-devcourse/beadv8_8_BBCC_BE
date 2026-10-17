package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductQuery;
import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductSummary;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.SellerProductDetailResult;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductInfoValue;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionValue;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.SizeSpec;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 본인의 상품 조회 유스케이스.
 */
@Service
@RequiredArgsConstructor
public class SellerProductService {

    private final SellerProductQuery sellerProductQuery;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CategoryRepository categoryRepository;

    // 정렬은 등록 최신순으로 고정이라 pageable의 sort는 무시된다
    @Transactional(readOnly = true)
    public Page<SellerProductSummary> findMyProducts(Long sellerId, Pageable pageable) {
        return sellerProductQuery.findBySeller(sellerId, pageable);
    }

    // 내 상품 상세를 돌려준다. 엔티티는 트랜잭션 안에서 읽어 결과로 모두 바꾼다
    @Transactional(readOnly = true)
    public SellerProductDetailResult findMyProductDetail(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.validateViewer(sellerId);
        Map<Long, Integer> availableQtyByOptionId = availableQtyByOptionId(product.getOptions());
        List<SellerProductDetailResult.Option> options = new ArrayList<>();
        for (ProductOption option : product.getOptions()) {
            // 보관 옵션은 판매자 화면에도 보이지 않는다 (SALE-06)
            if (option.isArchived()) {
                continue;
            }
            // 재고 행이 없는 옵션은 판매 가능 수량 0으로 본다
            options.add(new SellerProductDetailResult.Option(
                option.getId(),
                option.getName(),
                optionValueIds(option),
                option.getPrice(),
                availableQtyByOptionId.getOrDefault(option.getId(), 0),
                option.getSaleStatus().name(),
                option.getSkuCode()));
        }
        return new SellerProductDetailResult(
            product.getId(), product.getName(), product.getStatus().name(), product.getVersion(),
            product.getBrand(), product.getCategoryId(), product.getDescription(), product.getThumbnailUrl(),
            new ArrayList<>(product.getImageUrls()), attributes(product), options,
            prices(product), sizeSpecs(product));
    }

    // 상품의 정보 값을 정보 항목마다 한 칸으로 모은다. 칸은 항목이 처음 나온 순서, 목록 값 ID는 저장된 순서다
    private List<SellerProductDetailResult.Attribute> attributes(Product product) {
        Map<Long, List<Long>> valueIdsByAttributeId = new LinkedHashMap<>();
        Map<Long, String> textByAttributeId = new HashMap<>();
        for (ProductInfoValue infoValue : product.getInfoValues()) {
            Long attributeId = infoValue.getAttribute().getId();
            List<Long> valueIds = valueIdsByAttributeId.computeIfAbsent(attributeId, id -> new ArrayList<>());
            if (infoValue.getAttributeValue() != null) {
                valueIds.add(infoValue.getAttributeValue().getId());
            }
            if (infoValue.getTextValue() != null) {
                textByAttributeId.put(attributeId, infoValue.getTextValue());
            }
        }
        List<SellerProductDetailResult.Attribute> attributes = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : valueIdsByAttributeId.entrySet()) {
            attributes.add(new SellerProductDetailResult.Attribute(
                entry.getKey(), entry.getValue(), textByAttributeId.get(entry.getKey())));
        }
        return attributes;
    }

    // 옵션 값의 ID를 저장된 순서대로 모은다
    private List<Long> optionValueIds(ProductOption option) {
        List<Long> valueIds = new ArrayList<>();
        for (ProductOptionValue value : option.getValues()) {
            valueIds.add(value.getAttributeValue().getId());
        }
        return valueIds;
    }

    // 상품 유형으로 보관하지 않은 옵션의 판매가에서 가격 구분 묶음별 가격을 만든다
    private List<SellerProductDetailResult.Price> prices(Product product) {
        Category category = categoryRepository.findById(product.getCategoryId())
            .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        ProductType productType = category.productTypeToRegister();
        List<SellerProductDetailResult.Price> prices = new ArrayList<>();
        for (Product.PriceInfo price : product.currentPrices(productType)) {
            List<Long> valueIds = new ArrayList<>();
            for (AttributeValue value : price.values()) {
                valueIds.add(value.getId());
            }
            prices.add(new SellerProductDetailResult.Price(valueIds, price.price()));
        }
        return prices;
    }

    // 상품의 실측을 저장된 순서대로 결과로 바꾼다. 실측 값 맵은 복사해서 담는다
    private List<SellerProductDetailResult.SizeSpec> sizeSpecs(Product product) {
        List<SellerProductDetailResult.SizeSpec> sizeSpecs = new ArrayList<>();
        for (SizeSpec sizeSpec : product.getSizeSpecs()) {
            sizeSpecs.add(new SellerProductDetailResult.SizeSpec(
                sizeSpec.getSizeValue().getId(),
                sizeSpec.getMinHeightCm(),
                sizeSpec.getMaxHeightCm(),
                new LinkedHashMap<>(sizeSpec.getMeasurements())));
        }
        return sizeSpecs;
    }

    // 옵션들의 재고를 한 번에 읽어 옵션 ID별 판매 가능 수량으로 묶는다
    private Map<Long, Integer> availableQtyByOptionId(List<ProductOption> options) {
        List<Long> optionIds = new ArrayList<>();
        for (ProductOption option : options) {
            optionIds.add(option.getId());
        }
        return inventoryRepository.findAvailableQtyByOptionId(optionIds);
    }
}
