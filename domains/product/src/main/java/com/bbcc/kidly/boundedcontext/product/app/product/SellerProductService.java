package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductFilter;
import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductQuery;
import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductSummary;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.SellerProductDetailResult;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductInfoValue;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionValue;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.SizeSpec;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
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

@Service
@RequiredArgsConstructor
public class SellerProductService {

    private final SellerProductQuery sellerProductQuery;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public Page<SellerProductSummary> findMyProducts(
        Long sellerId, SellerProductFilter filter, Pageable pageable) {
        return sellerProductQuery.findBySeller(sellerId, filter, pageable);
    }

    @Transactional(readOnly = true)
    public SellerProductDetailResult findMyProductDetail(Long sellerId, Long productId) {
        var product = productRepository.getById(productId);
        product.validateViewer(sellerId);
        Map<Long, Integer> availableQtyByOptionId = availableQtyByOptionId(product.getOptions());
        var options = new ArrayList<SellerProductDetailResult.Option>();
        for (ProductOption option : product.getOptions()) {
            if (option.isArchived()) {
                continue;
            }
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

    private List<SellerProductDetailResult.Attribute> attributes(Product product) {
        var valueIdsByAttributeId = new LinkedHashMap<Long, List<Long>>();
        var textByAttributeId = new HashMap<Long, String>();
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
        var attributes = new ArrayList<SellerProductDetailResult.Attribute>();
        for (Map.Entry<Long, List<Long>> entry : valueIdsByAttributeId.entrySet()) {
            attributes.add(new SellerProductDetailResult.Attribute(
                entry.getKey(), entry.getValue(), textByAttributeId.get(entry.getKey())));
        }
        return attributes;
    }

    private List<Long> optionValueIds(ProductOption option) {
        var valueIds = new ArrayList<Long>();
        for (ProductOptionValue value : option.getValues()) {
            valueIds.add(value.getAttributeValue().getId());
        }
        return valueIds;
    }

    private List<SellerProductDetailResult.Price> prices(Product product) {
        var category = categoryRepository.getById(product.getCategoryId());
        ProductType productType = category.productTypeToRegister();
        var prices = new ArrayList<SellerProductDetailResult.Price>();
        for (Product.PriceInfo price : product.currentPrices(productType)) {
            var valueIds = new ArrayList<Long>();
            for (AttributeValue value : price.values()) {
                valueIds.add(value.getId());
            }
            prices.add(new SellerProductDetailResult.Price(valueIds, price.price()));
        }
        return prices;
    }

    private List<SellerProductDetailResult.SizeSpec> sizeSpecs(Product product) {
        var sizeSpecs = new ArrayList<SellerProductDetailResult.SizeSpec>();
        for (SizeSpec sizeSpec : product.getSizeSpecs()) {
            sizeSpecs.add(new SellerProductDetailResult.SizeSpec(
                sizeSpec.getSizeValue().getId(),
                sizeSpec.getMinHeightCm(),
                sizeSpec.getMaxHeightCm(),
                new LinkedHashMap<>(sizeSpec.getMeasurements())));
        }
        return sizeSpecs;
    }

    private Map<Long, Integer> availableQtyByOptionId(List<ProductOption> options) {
        List<Long> optionIds = options.stream().map(ProductOption::getId).toList();
        return inventoryRepository.findAvailableQtyByOptionId(optionIds);
    }
}
