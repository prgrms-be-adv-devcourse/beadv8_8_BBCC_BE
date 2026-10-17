package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;

public interface ProductTypeRepository {

    // 상품 유형을 저장한다. 연결된 항목과 허용 값도 함께 저장한다
    ProductType save(ProductType productType);

    // ID로 상품 유형을 찾는다
    Optional<ProductType> findById(Long id);

    // 모든 상품 유형을 ID 순서로 돌려준다
    List<ProductType> findAll();

    // 저장된 상품 유형의 수를 돌려준다
    long count();

    // 상품 유형을 ID로 찾는다. 없으면 404 오류(PRODUCT_TYPE_NOT_FOUND)를 던진다
    default ProductType getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(ProductTypeErrorCode.PRODUCT_TYPE_NOT_FOUND));
    }
}
