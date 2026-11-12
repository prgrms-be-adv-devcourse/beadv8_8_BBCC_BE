package com.bbcc.kidly.boundedcontext.settlement.out;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSettlementRepository extends JpaRepository<Settlement, Long>, SettlementRepository {

    @Override
    @Query("select min(s.completedAt) from Settlement s where s.status = :status")
    Optional<LocalDateTime> findOldestCompletedAt(@Param("status") SettlementStatus status);

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default Settlement getById(Long id) {
        return SettlementRepository.super.getById(id);
    }
}
