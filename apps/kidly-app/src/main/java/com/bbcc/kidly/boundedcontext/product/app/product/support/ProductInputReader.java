package com.bbcc.kidly.boundedcontext.product.app.product.support;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.InfoValueInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.OptionInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.PriceInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SizeSpecInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 판매자가 보낸 상품 입력(정보 항목 · 옵션 · 가격 · 실측)의 ID를 상품 유형에서 읽은 항목 · 값 객체로 바꿔,
 * 도메인에 넘길 Info로 만든다. 상품을 등록할 때와 고칠 때 같은 변환을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class ProductInputReader {

    private final AttributeValueRepository attributeValueRepository;

    /**
     * 유형의 입력 항목과 그 값을 ID로 찾는 맵이다. 항목 · 값은 카테고리와 같은 영속성 컨텍스트의 객체여야
     * 상품이 객체 동일성으로 검사할 수 있다.
     */
    public record InputLookup(Map<Long, Attribute> attributes, Map<Long, AttributeValue> values) {
    }

    // 유형의 입력 항목과 그 목록 값을 한 번에 읽는다
    public InputLookup readInputs(ProductType type) {
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

    public List<InfoValueInfo> toInfoValues(List<ProductRegisterRequest.AttributeInput> inputs, InputLookup lookup) {
        List<InfoValueInfo> infoValues = new ArrayList<>();
        for (ProductRegisterRequest.AttributeInput input : inputs) {
            Attribute attribute = find(lookup.attributes(), input.attributeId(), ProductErrorCode.NOT_INFO_ATTRIBUTE);
            List<AttributeValue> values = valuesOf(input.valueIds(), lookup, ProductErrorCode.INFO_VALUE_NOT_ALLOWED);
            infoValues.add(new InfoValueInfo(attribute, values, input.text()));
        }
        return infoValues;
    }

    public List<OptionInfo> toOptionInfos(List<ProductRegisterRequest.OptionInput> inputs, InputLookup lookup) {
        List<OptionInfo> options = new ArrayList<>();
        for (ProductRegisterRequest.OptionInput option : inputs) {
            List<AttributeValue> values =
                valuesOf(option.valueIds(), lookup, ProductErrorCode.OPTION_VALUE_NOT_ALLOWED);
            options.add(new OptionInfo(values, option.skuCode()));
        }
        return options;
    }

    public List<PriceInfo> toPriceInfos(List<ProductRegisterRequest.PriceInput> inputs, InputLookup lookup) {
        List<PriceInfo> prices = new ArrayList<>();
        for (ProductRegisterRequest.PriceInput price : inputs) {
            List<AttributeValue> values = valuesOf(price.valueIds(), lookup, ProductErrorCode.PRICE_GROUP_MISMATCH);
            prices.add(new PriceInfo(values, price.price()));
        }
        return prices;
    }

    public List<SizeSpecInfo> toSizeSpecInfos(List<ProductRegisterRequest.SizeSpecInput> inputs, InputLookup lookup) {
        List<SizeSpecInfo> specs = new ArrayList<>();
        for (ProductRegisterRequest.SizeSpecInput spec : inputs) {
            AttributeValue sizeValue = find(lookup.values(), spec.sizeValueId(), ProductErrorCode.SIZE_NOT_IN_OPTIONS);
            specs.add(new SizeSpecInfo(sizeValue, spec.minHeightCm(), spec.maxHeightCm(), spec.measurements()));
        }
        return specs;
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
