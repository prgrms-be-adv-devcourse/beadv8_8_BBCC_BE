package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnosisRepository;
import com.bbcc.kidly.boundedcontext.order.domain.stuck.StuckOrderDiagnosis;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 멈춘 주문 AI 진단 메모 저장소 포트를 Spring Data JPA로 구현한다(#926).
 */
public interface JpaStuckOrderDiagnosisRepository extends Repository<StuckOrderDiagnosis, Long>,
    StuckOrderDiagnosisRepository {

    @Override
    @Query("select d from StuckOrderDiagnosis d where d.orderId in :orderIds"
        + " and d.status = com.bbcc.kidly.boundedcontext.order.domain.stuck.StuckOrderDiagnosis.Status.DIAGNOSED"
        + " and d.id = (select max(l.id) from StuckOrderDiagnosis l where l.orderId = d.orderId"
        + " and l.stuckKind = d.stuckKind"
        + " and l.status = com.bbcc.kidly.boundedcontext.order.domain.stuck.StuckOrderDiagnosis.Status.DIAGNOSED)")
    List<StuckOrderDiagnosis> findLatestDiagnosed(Collection<Long> orderIds);

    @Override
    @Query("select d.orderId as orderId, d.historyMark as historyMark from StuckOrderDiagnosis d"
        + " where d.stuckKind = :stuckKind and d.orderId in :orderIds")
    List<RequestedState> findRequestedStates(String stuckKind, Collection<Long> orderIds);
}
