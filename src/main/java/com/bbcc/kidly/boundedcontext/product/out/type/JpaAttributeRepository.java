package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAttributeRepository extends JpaRepository<Attribute, Long>, AttributeRepository {
}
