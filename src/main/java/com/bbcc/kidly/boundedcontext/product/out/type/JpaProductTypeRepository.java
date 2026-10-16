package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProductTypeRepository extends JpaRepository<ProductType, Long>, ProductTypeRepository {
}
