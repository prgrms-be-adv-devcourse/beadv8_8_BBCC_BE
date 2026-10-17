package com.bbcc.kidly.boundedcontext.product.domain.type;

import java.util.List;
import java.util.Optional;

public interface AttributeValueRepository {

    // 목록 값을 저장한다
    AttributeValue save(AttributeValue attributeValue);

    // ID로 목록 값을 찾는다
    Optional<AttributeValue> findById(Long id);

    // 항목들의 목록 값을 ID 순서로 돌려준다
    List<AttributeValue> findAllOfAttributes(List<Attribute> attributes);
}
