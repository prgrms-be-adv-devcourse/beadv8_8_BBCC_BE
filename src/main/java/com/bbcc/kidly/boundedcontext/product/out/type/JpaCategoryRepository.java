package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
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
    @Query("select c from Category c where c.parent is null and c.name = :name and c.deletedAt is null")
    Optional<Category> findTopLevelByName(@Param("name") String name);

    @Override
    // PESSIMISTIC_WRITE는 PostgreSQL에서 FOR NO KEY UPDATE가 되어, 상품 insert가 외래 키 검사로 거는 KEY SHARE와 충돌하지 않는다.
    // 첫 상품 등록의 커밋을 기다려야 하므로 FOR UPDATE를 직접 쓴다
    @Query(value = "select * from product.category where id = :id for update", nativeQuery = true)
    Optional<Category> findByIdForUpdate(@Param("id") Long id);
}
