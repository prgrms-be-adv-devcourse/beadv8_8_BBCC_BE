package com.bbcc.kidly.boundedcontext.settlement.out;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSettlementRepository extends JpaRepository<Settlement, Long>, SettlementRepository {

    // JpaRepository.getById(프록시 반환)와 충돌하므로 도메인 쪽 getById(404)를 명시적으로 고른다
    @Override
    default Settlement getById(Long id) {
        return SettlementRepository.super.getById(id);
    }
}
