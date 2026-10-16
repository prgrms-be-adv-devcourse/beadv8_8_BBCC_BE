package com.bbcc.kidly.boundedcontext.product.domain.type;

public interface ProductTypeRepository {

    // 상품 유형을 저장한다. 연결된 항목과 허용 값도 함께 저장한다
    ProductType save(ProductType productType);

    // 저장된 상품 유형의 수를 돌려준다
    long count();
}
