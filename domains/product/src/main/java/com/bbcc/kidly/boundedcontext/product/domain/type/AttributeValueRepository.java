package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;

public interface AttributeValueRepository {

    AttributeValue save(AttributeValue attributeValue);

    Optional<AttributeValue> findById(Long id);

    List<AttributeValue> findAllOfAttributes(List<Attribute> attributes);

    List<AttributeValue> findAllByAttributeInAndDeletedAtIsNullOrderByIdAsc(List<Attribute> attributes);

    default AttributeValue getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(AttributeErrorCode.ATTRIBUTE_VALUE_NOT_FOUND));
    }
}
