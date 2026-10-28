package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAttributeValueRepository extends JpaRepository<AttributeValue, Long>, AttributeValueRepository {

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default AttributeValue getById(Long id) {
        return AttributeValueRepository.super.getById(id);
    }

    @Override
    @Query("select v from AttributeValue v where v.attribute in :attributes order by v.id")
    List<AttributeValue> findAllOfAttributes(@Param("attributes") List<Attribute> attributes);
}
