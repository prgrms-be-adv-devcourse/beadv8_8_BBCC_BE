package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaProductTypeRepository extends JpaRepository<ProductType, Long>, ProductTypeRepository {

    @Override
    @Query("select t from ProductType t order by t.id")
    List<ProductType> findAll();
}
