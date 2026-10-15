package com.bbcc.kidly.boundedcontext.product.out.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaProductRepository extends JpaRepository<Product, Long>, ProductRepository {

    @Override
    @Query("SELECT o FROM ProductOption o JOIN FETCH o.product WHERE o.id IN :optionIds")
    List<ProductOption> findOptionsWithProduct(@Param("optionIds") Collection<Long> optionIds);
}
