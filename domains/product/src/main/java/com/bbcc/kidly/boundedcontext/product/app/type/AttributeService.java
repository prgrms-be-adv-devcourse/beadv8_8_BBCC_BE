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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttributeService {

    private final AttributeRepository attributeRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final ProductTypeRepository productTypeRepository;

    // 항목을 저장하고 목록 값을 요청한 순서대로 저장한다. 그래서 값 ID가 넣은 순서대로 붙는다
    @Transactional
    public AttributeResult create(AttributeCreateRequest request) {
        Attribute attribute = attributeRepository.save(Attribute.create(
            request.name(), request.code(), AttributeInputType.valueOf(request.inputType())));
        List<AttributeResult.Value> values = new ArrayList<>();
        for (AttributeValueRequest input : request.values()) {
            values.add(saveValue(attribute, input));
        }
        return new AttributeResult(
            attribute.getId(), attribute.getName(), attribute.getCode(), attribute.getInputType().name(), values);
    }

    // 지우지 않은 모든 항목을 ID 순서로 돌려준다. 목록 값은 한 번의 조회로 가져와 항목별로 묶는다
    @Transactional(readOnly = true)
    public List<AttributeResult> findAll() {
        List<Attribute> attributes = attributeRepository.findAllByDeletedAtIsNullOrderByIdAsc();
        if (attributes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AttributeResult.Value>> valuesByAttributeId = new HashMap<>();
        List<AttributeValue> values =
            attributeValueRepository.findAllByAttributeInAndDeletedAtIsNullOrderByIdAsc(attributes);
        for (AttributeValue value : values) {
            valuesByAttributeId.computeIfAbsent(value.getAttribute().getId(), id -> new ArrayList<>())
                .add(new AttributeResult.Value(value.getId(), value.getName(), value.getColorCode()));
        }
        List<AttributeResult> results = new ArrayList<>();
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

    // 항목에 목록 값 하나를 더한다
    @Transactional
    public AttributeResult.Value addValue(Long attributeId, AttributeValueRequest request) {
        Attribute attribute = attributeRepository.getById(attributeId);
        return saveValue(attribute, request);
    }

    private AttributeResult.Value saveValue(Attribute attribute, AttributeValueRequest input) {
        AttributeValue value = attributeValueRepository.save(
            AttributeValue.create(attribute, input.name(), input.colorCode()));
        return new AttributeResult.Value(value.getId(), value.getName(), value.getColorCode());
    }

    // 항목을 지운다. 지우지 않은 상품 유형에 연결된 항목인지는 읽어 넘기고, 지울 수 있는지는 항목이 판단한다 (TYPE-10)
    @Transactional
    public void delete(Long attributeId) {
        // 쓰이는 중인지 읽은 뒤 지우기 전에 다른 요청이 연결하지 못하게 한다(TYPE-10)
        Attribute attribute = attributeRepository.getByIdForUpdate(attributeId);
        boolean inUse = productTypeRepository.existsByAttributesAttributeAndDeletedAtIsNull(attribute);
        attribute.delete(LocalDateTime.now(), inUse);
    }

    // 목록 값을 지운다. 다른 항목의 값이면 없는 값으로 본다. 목록 값은 막는 경우가 없다
    @Transactional
    public void deleteValue(Long attributeId, Long valueId) {
        AttributeValue value = attributeValueRepository.getById(valueId);
        if (!value.getAttribute().getId().equals(attributeId)) {
            throw new BusinessException(AttributeErrorCode.ATTRIBUTE_VALUE_NOT_FOUND);
        }
        value.delete(LocalDateTime.now());
    }
}
