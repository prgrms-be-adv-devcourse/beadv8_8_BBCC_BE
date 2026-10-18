package com.bbcc.kidly.boundedcontext.product.out.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAttributeRepository extends JpaRepository<Attribute, Long>, AttributeRepository {

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default Attribute getById(Long id) {
        return AttributeRepository.super.getById(id);
    }

    @Override
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from Attribute e where e.id = :id")
    Optional<Attribute> findByIdForShare(@Param("id") Long id);

    // PESSIMISTIC_WRITE는 PostgreSQL에서 FOR NO KEY UPDATE가 되어, 연결하는 행 insert가 외래 키 검사로 거는 KEY SHARE와 충돌하지 않는다.
    // 연결의 커밋을 기다려야 하므로 FOR UPDATE를 직접 쓴다
    @Override
    @Query(value = "select * from product.attribute where id = :id for update", nativeQuery = true)
    Optional<Attribute> findByIdForUpdate(@Param("id") Long id);
}
