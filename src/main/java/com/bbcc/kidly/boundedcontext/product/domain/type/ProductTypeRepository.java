package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;

public interface ProductTypeRepository {

    // 상품 유형을 저장한다. 연결된 항목과 허용 값도 함께 저장한다
    ProductType save(ProductType productType);

    // ID로 상품 유형을 찾는다
    Optional<ProductType> findById(Long id);

    // 지우지 않은 상품 유형만 ID 순서로 돌려준다. 관리자 목록에 쓴다 (TYPE-09)
    List<ProductType> findAllByDeletedAtIsNullOrderByIdAsc();

    // 저장된 상품 유형의 수를 돌려준다
    long count();

    // 상품 유형을 ID로 찾는다. 없으면 404 오류(PRODUCT_TYPE_NOT_FOUND)를 던진다
    default ProductType getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(ProductTypeErrorCode.PRODUCT_TYPE_NOT_FOUND));
    }

    // 상품 유형을 공유 잠금(FOR SHARE)으로 ID로 찾는다. 없으면 빈 값을 돌려준다
    Optional<ProductType> findByIdForShare(Long id);

    // 상품 유형을 공유 잠금(FOR SHARE)으로 ID로 찾는다. 없으면 404 오류(PRODUCT_TYPE_NOT_FOUND)를 던진다
    default ProductType getByIdForShare(Long id) {
        return findByIdForShare(id)
            .orElseThrow(() -> new BusinessException(ProductTypeErrorCode.PRODUCT_TYPE_NOT_FOUND));
    }

    // 지우지 않은 유형 중 그 항목을 연결한 유형이 있는지 본다. 유형에 연결된 항목은 지우지 않는다 (TYPE-10)
    boolean existsByAttributesAttributeAndDeletedAtIsNull(Attribute attribute);
}
