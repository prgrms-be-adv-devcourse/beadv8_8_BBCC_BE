package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // Hibernate 7의 PESSIMISTIC_WRITE는 PostgreSQL에서 FOR NO KEY UPDATE가 되어, 다른 표에서 FK로 이 행을
    // 가리키는 행을 넣을 때 거는 KEY SHARE와 충돌하지 않는다. 연결하는 쪽이 모두 FOR SHARE를 걸지만,
    // FOR UPDATE로 두면 공유 잠금을 빠뜨린 연결 경로가 생겨도 FK 검사만으로 그 커밋을 기다리게 하는 방어선이 된다
    @Override
    @Query(value = "select * from product.category where id = :id for update", nativeQuery = true)
    Optional<Category> findByIdForUpdate(@Param("id") Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Category c where c.id = :id")
    Optional<Category> findByIdForShare(@Param("id") Long id);
}
