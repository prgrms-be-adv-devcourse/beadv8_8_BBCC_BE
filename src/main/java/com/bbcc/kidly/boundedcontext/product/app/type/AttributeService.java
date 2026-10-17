package com.bbcc.kidly.boundedcontext.product.app.type;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeCreateRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeValueRequest;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
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

    // 모든 항목을 ID 순서로 돌려준다. 목록 값은 한 번의 조회로 가져와 항목별로 묶는다
    @Transactional(readOnly = true)
    public List<AttributeResult> findAll() {
        List<Attribute> attributes = attributeRepository.findAll();
        if (attributes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AttributeResult.Value>> valuesByAttributeId = new HashMap<>();
        for (AttributeValue value : attributeValueRepository.findAllOfAttributes(attributes)) {
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
}
