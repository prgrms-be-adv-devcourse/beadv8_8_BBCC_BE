package com.bbcc.kidly.boundedcontext.product.domain.type;

import java.util.Optional;

public interface AttributeRepository {

    // 항목을 저장한다
    Attribute save(Attribute attribute);

    // ID로 항목을 찾는다
    Optional<Attribute> findById(Long id);
}
