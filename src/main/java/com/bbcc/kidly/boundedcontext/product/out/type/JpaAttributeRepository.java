package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaAttributeRepository extends JpaRepository<Attribute, Long>, AttributeRepository {

    @Override
    @Query("select a from Attribute a order by a.id")
    List<Attribute> findAll();
}
