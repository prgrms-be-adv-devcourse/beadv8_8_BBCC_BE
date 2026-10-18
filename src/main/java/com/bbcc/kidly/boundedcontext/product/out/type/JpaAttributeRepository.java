package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAttributeRepository extends JpaRepository<Attribute, Long>, AttributeRepository {

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default Attribute getById(Long id) {
        return AttributeRepository.super.getById(id);
    }
}
