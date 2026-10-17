package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCategoryRepository extends JpaRepository<Category, Long>, CategoryRepository {

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default Category getById(Long id) {
        return CategoryRepository.super.getById(id);
    }

    @Override
    @Query("select c from Category c where c.parent is null and c.name = :name")
    Optional<Category> findTopLevelByName(@Param("name") String name);

    @Override
    @Query("select c from Category c order by c.id")
    List<Category> findAll();
}
