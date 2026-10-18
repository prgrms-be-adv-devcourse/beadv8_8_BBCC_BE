package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProductTypeRepository extends JpaRepository<ProductType, Long>, ProductTypeRepository {

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default ProductType getById(Long id) {
        return ProductTypeRepository.super.getById(id);
    }
}
