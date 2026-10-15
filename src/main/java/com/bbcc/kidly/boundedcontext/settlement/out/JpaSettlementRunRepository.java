package com.bbcc.kidly.boundedcontext.settlement.out;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRun;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRunRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSettlementRunRepository extends JpaRepository<SettlementRun, Long>, SettlementRunRepository {

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default SettlementRun getById(Long id) {
        return SettlementRunRepository.super.getById(id);
    }
}
