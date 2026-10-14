package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAttributeValueRepository extends JpaRepository<AttributeValue, Long>, AttributeValueRepository {

    @Override
    @Query("select v from AttributeValue v where v.attribute in :attributes order by v.id")
    List<AttributeValue> findAllOfAttributes(@Param("attributes") List<Attribute> attributes);
}
