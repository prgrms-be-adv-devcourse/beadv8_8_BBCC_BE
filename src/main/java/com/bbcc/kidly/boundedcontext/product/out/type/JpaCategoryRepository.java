package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCategoryRepository extends JpaRepository<Category, Long>, CategoryRepository {

    @Override
    @Query("select c from Category c where c.parent is null and c.name = :name")
    Optional<Category> findRootByName(@Param("name") String name);
}
