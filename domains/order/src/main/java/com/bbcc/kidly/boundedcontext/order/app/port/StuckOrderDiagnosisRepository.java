package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.stuck.StuckOrderDiagnosis;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 멈춘 주문 AI 진단 메모 저장소(#926).
 */
public interface StuckOrderDiagnosisRepository {

    StuckOrderDiagnosis save(StuckOrderDiagnosis diagnosis);

    /**
     * 그 시각 이후 요청 수(하루 상한). 서버가 여러 대여도 DB 한 곳에서 센다.
     */
    long countByRequestedAtGreaterThanEqual(LocalDateTime from);

    /**
     * 주문들의 (주문, 멈춘 종류)마다 가장 최근 성공 메모.
     */
    List<StuckOrderDiagnosis> findLatestDiagnosed(Collection<Long> orderIds);

    /**
     * 그 종류에서 주문들에 이미 요청한 상태(성공 · 실패 모두). 한 페이지를 쿼리 한 번으로 거른다.
     */
    List<RequestedState> findRequestedStates(String stuckKind, Collection<Long> orderIds);

    interface RequestedState {

        Long getOrderId();

        long getHistoryMark();
    }
}
