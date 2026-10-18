package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;

public interface AttributeValueRepository {

    // 목록 값을 저장한다
    AttributeValue save(AttributeValue attributeValue);

    // ID로 목록 값을 찾는다
    Optional<AttributeValue> findById(Long id);

    // 항목들의 목록 값을 ID 순서로 돌려준다
    List<AttributeValue> findAllOfAttributes(List<Attribute> attributes);

    // 항목들의 지우지 않은 목록 값만 ID 순서로 돌려준다. 선택지 조회에 쓴다 (TYPE-09)
    List<AttributeValue> findAllByAttributeInAndDeletedAtIsNullOrderByIdAsc(List<Attribute> attributes);

    // 목록 값을 ID로 찾는다. 없으면 404 오류(ATTRIBUTE_VALUE_NOT_FOUND)를 던진다
    default AttributeValue getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(AttributeErrorCode.ATTRIBUTE_VALUE_NOT_FOUND));
    }
}
