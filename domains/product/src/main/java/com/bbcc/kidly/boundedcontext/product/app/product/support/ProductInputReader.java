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

@Component
@RequiredArgsConstructor
public class ProductInputReader {

    private final AttributeValueRepository attributeValueRepository;

    public record InputLookup(Map<Long, Attribute> attributes, Map<Long, AttributeValue> values) {
    }

    public InputLookup readInputs(ProductType type) {
        var attributes = new ArrayList<Attribute>();
        var attributesById = new HashMap<Long, Attribute>();
        for (ProductTypeAttribute link : type.inputAttributes()) {
            attributes.add(link.getAttribute());
            attributesById.put(link.getAttribute().getId(), link.getAttribute());
        }
        var valuesById = new HashMap<Long, AttributeValue>();
        for (AttributeValue value : attributeValueRepository.findAllOfAttributes(attributes)) {
            valuesById.put(value.getId(), value);
        }
        return new InputLookup(attributesById, valuesById);
    }

    public List<InfoValueInfo> toInfoValues(List<ProductRegisterRequest.AttributeInput> inputs, InputLookup lookup) {
        var infoValues = new ArrayList<InfoValueInfo>();
        for (ProductRegisterRequest.AttributeInput input : inputs) {
            Attribute attribute = find(lookup.attributes(), input.attributeId(), ProductErrorCode.NOT_INFO_ATTRIBUTE);
            List<AttributeValue> values = valuesOf(input.valueIds(), lookup, ProductErrorCode.INFO_VALUE_NOT_ALLOWED);
            infoValues.add(new InfoValueInfo(attribute, values, input.text()));
        }
        return infoValues;
    }

    public List<OptionInfo> toOptionInfos(List<ProductRegisterRequest.OptionInput> inputs, InputLookup lookup) {
        var options = new ArrayList<OptionInfo>();
        for (ProductRegisterRequest.OptionInput option : inputs) {
            List<AttributeValue> values =
                valuesOf(option.valueIds(), lookup, ProductErrorCode.OPTION_VALUE_NOT_ALLOWED);
            options.add(new OptionInfo(values, option.skuCode()));
        }
        return options;
    }

    public List<PriceInfo> toPriceInfos(List<ProductRegisterRequest.PriceInput> inputs, InputLookup lookup) {
        var prices = new ArrayList<PriceInfo>();
        for (ProductRegisterRequest.PriceInput price : inputs) {
            List<AttributeValue> values = valuesOf(price.valueIds(), lookup, ProductErrorCode.PRICE_GROUP_MISMATCH);
            prices.add(new PriceInfo(values, price.price()));
        }
        return prices;
    }

    public List<SizeSpecInfo> toSizeSpecInfos(List<ProductRegisterRequest.SizeSpecInput> inputs, InputLookup lookup) {
        var specs = new ArrayList<SizeSpecInfo>();
        for (ProductRegisterRequest.SizeSpecInput spec : inputs) {
            AttributeValue sizeValue = find(lookup.values(), spec.sizeValueId(), ProductErrorCode.SIZE_NOT_IN_OPTIONS);
            specs.add(new SizeSpecInfo(sizeValue, spec.minHeightCm(), spec.maxHeightCm(), spec.measurements()));
        }
        return specs;
    }

    private List<AttributeValue> valuesOf(List<Long> valueIds, InputLookup lookup, ErrorCode errorCode) {
        var values = new ArrayList<AttributeValue>();
        for (Long valueId : valueIds) {
            values.add(find(lookup.values(), valueId, errorCode));
        }
        return values;
    }

    private <T> T find(Map<Long, T> entitiesById, Long id, ErrorCode errorCode) {
        T entity = entitiesById.get(id);
        if (entity == null) {
            throw new BusinessException(errorCode);
        }
        return entity;
    }
}
