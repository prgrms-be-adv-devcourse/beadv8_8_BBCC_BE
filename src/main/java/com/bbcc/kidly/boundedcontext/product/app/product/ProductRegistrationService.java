package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterResult;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.AddOptionsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.AddSizeSpecsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.InfoValueInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.OptionInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.PriceInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.RegisterInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SizeSpecInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductRegistrationService {

    private final CategoryRepository categoryRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    // 상품 · 옵션 · 실측을 판매대기 상태로 등록하고 옵션마다 초기 재고를 연다
    @Transactional
    public ProductRegisterResult register(Long sellerId, ProductRegisterRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        ProductType type = category.productTypeToRegister();
        InputLookup lookup = readInputs(type);

        Product product = Product.register(new RegisterInfo(
            sellerId,
            category,
            request.name(),
            request.brand(),
            request.description(),
            request.thumbnailUrl(),
            request.imageUrls(),
            toInfoValues(request.attributes(), lookup)));
        // 옵션 · 실측 목록이 비면 추가 메서드가 IllegalArgumentException(500)을 던지므로 부르지 않고,
        // 빠진 입력은 아래 판매 시작 입력 검사의 400이 알려 준다
        List<ProductOption> options = List.of();
        if (!request.options().isEmpty()) {
            options = product.addOptions(toAddOptionsInfo(sellerId, request, lookup), type);
        }
        if (!request.sizeSpecs().isEmpty()) {
            product.addSizeSpecs(toAddSizeSpecsInfo(sellerId, request, lookup), category, type);
        }
        product.validateSaleStartInputs(category, type);
        productRepository.save(product);

        List<ProductRegisterResult.Option> results = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            ProductOption option = options.get(i);
            int initialQuantity = request.options().get(i).initialQuantity();
            inventoryRepository.saveOpening(InventoryOpening.open(option, initialQuantity, sellerId));
            results.add(new ProductRegisterResult.Option(
                option.getId(),
                option.getName(),
                option.getPrice(),
                initialQuantity,
                option.getSaleStatus().name(),
                option.getSkuCode()));
        }
        return new ProductRegisterResult(
            product.getId(), product.getStatus().name(), product.getVersion(), results);
    }

    /**
     * 유형의 입력 항목과 그 값을 ID로 찾는 맵이다. 항목 · 값은 카테고리와 같은 영속성 컨텍스트의 객체여야
     * 상품이 객체 동일성으로 검사할 수 있다.
     */
    private record InputLookup(Map<Long, Attribute> attributes, Map<Long, AttributeValue> values) {
    }

    // 유형의 입력 항목과 그 목록 값을 한 번에 읽는다
    private InputLookup readInputs(ProductType type) {
        List<Attribute> attributes = new ArrayList<>();
        Map<Long, Attribute> attributesById = new HashMap<>();
        for (ProductTypeAttribute link : type.inputAttributes()) {
            attributes.add(link.getAttribute());
            attributesById.put(link.getAttribute().getId(), link.getAttribute());
        }
        Map<Long, AttributeValue> valuesById = new HashMap<>();
        for (AttributeValue value : attributeValueRepository.findAllOfAttributes(attributes)) {
            valuesById.put(value.getId(), value);
        }
        return new InputLookup(attributesById, valuesById);
    }

    private List<InfoValueInfo> toInfoValues(List<ProductRegisterRequest.AttributeInput> inputs, InputLookup lookup) {
        List<InfoValueInfo> infoValues = new ArrayList<>();
        for (ProductRegisterRequest.AttributeInput input : inputs) {
            infoValues.add(new InfoValueInfo(
                lookup.attributes().get(input.attributeId()), valuesOf(input.valueIds(), lookup), input.text()));
        }
        return infoValues;
    }

    private AddOptionsInfo toAddOptionsInfo(Long sellerId, ProductRegisterRequest request, InputLookup lookup) {
        List<OptionInfo> options = new ArrayList<>();
        for (ProductRegisterRequest.OptionInput option : request.options()) {
            options.add(new OptionInfo(valuesOf(option.valueIds(), lookup), option.skuCode()));
        }
        List<PriceInfo> prices = new ArrayList<>();
        for (ProductRegisterRequest.PriceInput price : request.prices()) {
            prices.add(new PriceInfo(valuesOf(price.valueIds(), lookup), price.price()));
        }
        return new AddOptionsInfo(sellerId, options, prices);
    }

    private AddSizeSpecsInfo toAddSizeSpecsInfo(Long sellerId, ProductRegisterRequest request, InputLookup lookup) {
        List<SizeSpecInfo> specs = new ArrayList<>();
        for (ProductRegisterRequest.SizeSpecInput spec : request.sizeSpecs()) {
            specs.add(new SizeSpecInfo(
                lookup.values().get(spec.sizeValueId()), spec.minHeightCm(), spec.maxHeightCm(), spec.measurements()));
        }
        return new AddSizeSpecsInfo(sellerId, specs);
    }

    private List<AttributeValue> valuesOf(List<Long> valueIds, InputLookup lookup) {
        List<AttributeValue> values = new ArrayList<>();
        for (Long valueId : valueIds) {
            values.add(lookup.values().get(valueId));
        }
        return values;
    }
}
