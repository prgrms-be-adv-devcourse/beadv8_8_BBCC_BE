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

    // 항목을 행 잠금(FOR UPDATE)으로 ID로 찾는다. 없으면 빈 값을 돌려준다
    Optional<Attribute> findByIdForUpdate(Long id);

    // 항목을 행 잠금(FOR UPDATE)으로 ID로 찾는다. 없으면 404 오류(ATTRIBUTE_NOT_FOUND)를 던진다
    // 같은 트랜잭션에서 이 엔티티를 이미 읽었으면 잠그기만 하고 값은 다시 읽지 않으므로, 트랜잭션에서 처음 읽을 때 쓴다.
    default Attribute getByIdForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> new BusinessException(AttributeErrorCode.ATTRIBUTE_NOT_FOUND));
    }

    // 항목을 공유 잠금(FOR SHARE)으로 ID로 찾는다. 없으면 빈 값을 돌려준다
    Optional<Attribute> findByIdForShare(Long id);

    // 항목을 공유 잠금(FOR SHARE)으로 ID로 찾는다. 없으면 404 오류(ATTRIBUTE_NOT_FOUND)를 던진다
    // 같은 트랜잭션에서 이 엔티티를 이미 읽었으면 잠그기만 하고 값은 다시 읽지 않으므로, 트랜잭션에서 처음 읽을 때 쓴다.
    default Attribute getByIdForShare(Long id) {
        return findByIdForShare(id).orElseThrow(() -> new BusinessException(AttributeErrorCode.ATTRIBUTE_NOT_FOUND));
    }

    // 항목을 ID로 찾는다. 없으면 404 오류(ATTRIBUTE_NOT_FOUND)를 던진다
    default Attribute getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(AttributeErrorCode.ATTRIBUTE_NOT_FOUND));
    }
}
