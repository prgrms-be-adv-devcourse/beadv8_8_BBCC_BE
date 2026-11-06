package com.bbcc.kidly.boundedcontext.seller.out;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerDeadLetter;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerDeadLetterRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSellerDeadLetterRepository
    extends JpaRepository<SellerDeadLetter, Long>, SellerDeadLetterRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SellerDeadLetter d where d.id = :id")
    Optional<SellerDeadLetter> findByIdForUpdate(@Param("id") Long id);

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default SellerDeadLetter getById(Long id) {
        return SellerDeadLetterRepository.super.getById(id);
    }
}
