package com.bbcc.kidly.boundedcontext.product.app.type;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeCreateRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeResult;
import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import java.util.ArrayList;
import java.util.List;
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
        for (AttributeCreateRequest.ValueInput input : request.values()) {
            AttributeValue value = attributeValueRepository.save(
                AttributeValue.create(attribute, input.name(), input.colorCode()));
            values.add(new AttributeResult.Value(value.getId(), value.getName(), value.getColorCode()));
        }
        return new AttributeResult(
            attribute.getId(), attribute.getName(), attribute.getCode(), attribute.getInputType().name(), values);
    }
}
