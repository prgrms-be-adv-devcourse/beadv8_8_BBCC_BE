package com.bbcc.kidly.boundedcontext.settlement.domain;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;

public interface SettlementRepository {

    Settlement save(Settlement settlement);

    Optional<Settlement> findById(Long id);

    Optional<Settlement> findByIdAndSellerId(Long id, Long sellerId);

    // 지급 결과를 못 받고 COMPLETED에 머문 정산서를 완료 시각 구간(양끝 포함)에서 afterId 다음부터 id 순으로 limit건 찾는다
    List<Settlement> findByStatusAndCompletedAtBetweenAndIdGreaterThanOrderByIdAsc(
        SettlementStatus status, LocalDateTime from, LocalDateTime to, Long afterId, Limit limit);

    // 기준 시각 이전에 완료됐지만 아직 지급되지 않은 정산서 수
    long countByStatusAndCompletedAtBefore(SettlementStatus status, LocalDateTime before);

    // 없으면 404
    default Settlement getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    // 없거나 다른 판매자의 정산서면 404
    default Settlement getByIdAndSellerId(Long id, Long sellerId) {
        return findByIdAndSellerId(id, sellerId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
