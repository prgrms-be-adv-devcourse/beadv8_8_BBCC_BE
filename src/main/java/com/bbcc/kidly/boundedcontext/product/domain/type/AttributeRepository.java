package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;

public interface AttributeRepository {

    // 항목을 저장한다
    Attribute save(Attribute attribute);

    // ID로 항목을 찾는다
    Optional<Attribute> findById(Long id);

    // 지우지 않은 항목만 ID 순서로 돌려준다. 관리자 목록에 쓴다 (TYPE-09)
    List<Attribute> findAllByDeletedAtIsNullOrderByIdAsc();

    // 항목을 ID로 찾는다. 없으면 404 오류(ATTRIBUTE_NOT_FOUND)를 던진다
    default Attribute getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(AttributeErrorCode.ATTRIBUTE_NOT_FOUND));
    }
}
