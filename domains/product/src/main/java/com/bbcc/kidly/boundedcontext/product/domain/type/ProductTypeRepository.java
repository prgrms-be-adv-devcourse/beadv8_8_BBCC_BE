package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;

public interface ProductTypeRepository {

    ProductType save(ProductType productType);

    Optional<ProductType> findById(Long id);

    List<ProductType> findAllByDeletedAtIsNullOrderByIdAsc();

    long count();

    default ProductType getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(ProductTypeErrorCode.PRODUCT_TYPE_NOT_FOUND));
    }

    Optional<ProductType> findByIdForUpdate(Long id);

    // 같은 트랜잭션에서 이 엔티티를 이미 읽었으면 잠그기만 하고 값은 다시 읽지 않으므로, 트랜잭션에서 처음 읽을 때 쓴다.
    default ProductType getByIdForUpdate(Long id) {
        return findByIdForUpdate(id)
            .orElseThrow(() -> new BusinessException(ProductTypeErrorCode.PRODUCT_TYPE_NOT_FOUND));
    }

    Optional<ProductType> findByIdForShare(Long id);

    // 같은 트랜잭션에서 이 엔티티를 이미 읽었으면 잠그기만 하고 값은 다시 읽지 않으므로, 트랜잭션에서 처음 읽을 때 쓴다.
    default ProductType getByIdForShare(Long id) {
        return findByIdForShare(id)
            .orElseThrow(() -> new BusinessException(ProductTypeErrorCode.PRODUCT_TYPE_NOT_FOUND));
    }

    boolean existsByAttributesAttributeAndDeletedAtIsNull(Attribute attribute);
}
