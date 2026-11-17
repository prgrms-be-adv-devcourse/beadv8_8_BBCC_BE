package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;

public interface AttributeRepository {

    Attribute save(Attribute attribute);

    Optional<Attribute> findById(Long id);

    List<Attribute> findAllByDeletedAtIsNullOrderByIdAsc();

    Optional<Attribute> findByIdForUpdate(Long id);

    // 같은 트랜잭션에서 이 엔티티를 이미 읽었으면 잠그기만 하고 값은 다시 읽지 않으므로, 트랜잭션에서 처음 읽을 때 쓴다.
    default Attribute getByIdForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> new BusinessException(AttributeErrorCode.ATTRIBUTE_NOT_FOUND));
    }

    Optional<Attribute> findByIdForShare(Long id);

    // 같은 트랜잭션에서 이 엔티티를 이미 읽었으면 잠그기만 하고 값은 다시 읽지 않으므로, 트랜잭션에서 처음 읽을 때 쓴다.
    default Attribute getByIdForShare(Long id) {
        return findByIdForShare(id).orElseThrow(() -> new BusinessException(AttributeErrorCode.ATTRIBUTE_NOT_FOUND));
    }

    default Attribute getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(AttributeErrorCode.ATTRIBUTE_NOT_FOUND));
    }
}
