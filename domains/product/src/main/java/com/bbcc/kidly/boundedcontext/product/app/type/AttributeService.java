package com.bbcc.kidly.boundedcontext.product.app.type;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeCreateRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeValueRequest;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttributeService {

    private final AttributeRepository attributeRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final ProductTypeRepository productTypeRepository;

    @Transactional
    public AttributeResult create(AttributeCreateRequest request) {
        Attribute attribute = attributeRepository.save(Attribute.create(
            request.name(), request.code(), AttributeInputType.valueOf(request.inputType())));
        var values = new ArrayList<AttributeResult.Value>();
        for (AttributeValueRequest input : request.values()) {
            values.add(saveValue(attribute, input));
        }
        return new AttributeResult(
            attribute.getId(), attribute.getName(), attribute.getCode(), attribute.getInputType().name(), values);
    }

    @Transactional(readOnly = true)
    public List<AttributeResult> findAll() {
        List<Attribute> attributes = attributeRepository.findAllByDeletedAtIsNullOrderByIdAsc();
        if (attributes.isEmpty()) {
            return List.of();
        }
        var valuesByAttributeId = new HashMap<Long, List<AttributeResult.Value>>();
        List<AttributeValue> values =
            attributeValueRepository.findAllByAttributeInAndDeletedAtIsNullOrderByIdAsc(attributes);
        for (AttributeValue value : values) {
            valuesByAttributeId.computeIfAbsent(value.getAttribute().getId(), id -> new ArrayList<>())
                .add(new AttributeResult.Value(value.getId(), value.getName(), value.getColorCode()));
        }
        var results = new ArrayList<AttributeResult>();
        for (Attribute attribute : attributes) {
            results.add(new AttributeResult(
                attribute.getId(),
                attribute.getName(),
                attribute.getCode(),
                attribute.getInputType().name(),
                valuesByAttributeId.getOrDefault(attribute.getId(), List.of())));
        }
        return results;
    }

    @Transactional
    public AttributeResult.Value addValue(Long attributeId, AttributeValueRequest request) {
        var attribute = attributeRepository.getById(attributeId);
        return saveValue(attribute, request);
    }

    private AttributeResult.Value saveValue(Attribute attribute, AttributeValueRequest input) {
        AttributeValue value = attributeValueRepository.save(
            AttributeValue.create(attribute, input.name(), input.colorCode()));
        return new AttributeResult.Value(value.getId(), value.getName(), value.getColorCode());
    }

    @Transactional
    public void delete(Long attributeId) {
        var attribute = attributeRepository.getByIdForUpdate(attributeId);
        boolean inUse = productTypeRepository.existsByAttributesAttributeAndDeletedAtIsNull(attribute);
        attribute.delete(LocalDateTime.now(), inUse);
    }

    @Transactional
    public void deleteValue(Long attributeId, Long valueId) {
        var value = attributeValueRepository.getById(valueId);
        if (!value.getAttribute().getId().equals(attributeId)) {
            throw new BusinessException(AttributeErrorCode.ATTRIBUTE_VALUE_NOT_FOUND);
        }
        value.delete(LocalDateTime.now());
    }
}
