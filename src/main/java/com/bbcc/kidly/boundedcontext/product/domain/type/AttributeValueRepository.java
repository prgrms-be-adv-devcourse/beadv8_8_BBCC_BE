package com.bbcc.kidly.boundedcontext.product.domain.type;

import java.util.List;

public interface AttributeValueRepository {

    // 항목들의 목록 값을 ID 순서로 돌려준다
    List<AttributeValue> findAllOfAttributes(List<Attribute> attributes);
}
