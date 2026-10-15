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
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.SkuCodes;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.ErrorCode;
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
            // 요청 옵션 자체의 검사를 먼저 하고, 다른 상품을 읽어야 하는 SKU 코드 검사는 그 뒤에 한다.
            // 상품은 아래에서 저장하므로 여기서 거절되면 아무것도 저장되지 않는다
            List<String> skuCodes = options.stream().map(ProductOption::getSkuCode).toList();
            SkuCodes.validateUnique(skuCodes, productRepository.findSkuCodesInUse(sellerId, skuCodes));
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
            Attribute attribute = find(lookup.attributes(), input.attributeId(), ProductErrorCode.NOT_INFO_ATTRIBUTE);
            List<AttributeValue> values = valuesOf(input.valueIds(), lookup, ProductErrorCode.INFO_VALUE_NOT_ALLOWED);
            infoValues.add(new InfoValueInfo(attribute, values, input.text()));
        }
        return infoValues;
    }

    private AddOptionsInfo toAddOptionsInfo(Long sellerId, ProductRegisterRequest request, InputLookup lookup) {
        List<OptionInfo> options = new ArrayList<>();
        for (ProductRegisterRequest.OptionInput option : request.options()) {
            List<AttributeValue> values =
                valuesOf(option.valueIds(), lookup, ProductErrorCode.OPTION_VALUE_NOT_ALLOWED);
            options.add(new OptionInfo(values, option.skuCode()));
        }
        List<PriceInfo> prices = new ArrayList<>();
        for (ProductRegisterRequest.PriceInput price : request.prices()) {
            List<AttributeValue> values = valuesOf(price.valueIds(), lookup, ProductErrorCode.PRICE_GROUP_MISMATCH);
            prices.add(new PriceInfo(values, price.price()));
        }
        return new AddOptionsInfo(sellerId, options, prices);
    }

    private AddSizeSpecsInfo toAddSizeSpecsInfo(Long sellerId, ProductRegisterRequest request, InputLookup lookup) {
        List<SizeSpecInfo> specs = new ArrayList<>();
        for (ProductRegisterRequest.SizeSpecInput spec : request.sizeSpecs()) {
            AttributeValue sizeValue = find(lookup.values(), spec.sizeValueId(), ProductErrorCode.SIZE_NOT_IN_OPTIONS);
            specs.add(new SizeSpecInfo(sizeValue, spec.minHeightCm(), spec.maxHeightCm(), spec.measurements()));
        }
        return new AddSizeSpecsInfo(sellerId, specs);
    }

    private List<AttributeValue> valuesOf(List<Long> valueIds, InputLookup lookup, ErrorCode errorCode) {
        List<AttributeValue> values = new ArrayList<>();
        for (Long valueId : valueIds) {
            values.add(find(lookup.values(), valueId, errorCode));
        }
        return values;
    }

    // 상품 유형에서 읽은 맵에 없는 ID이면 넘겨받은 오류로 거절한다
    private <T> T find(Map<Long, T> entitiesById, Long id, ErrorCode errorCode) {
        T entity = entitiesById.get(id);
        if (entity == null) {
            throw new BusinessException(errorCode);
        }
        return entity;
    }
}
