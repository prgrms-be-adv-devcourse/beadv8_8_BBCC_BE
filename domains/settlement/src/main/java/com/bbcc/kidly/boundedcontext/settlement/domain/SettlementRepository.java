package com.bbcc.kidly.boundedcontext.settlement.domain;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;

public interface SettlementRepository {

    Settlement save(Settlement settlement);

    Optional<Settlement> findById(Long id);

    Optional<Settlement> findByIdAndSellerId(Long id, Long sellerId);

    List<Settlement> findByStatusAndCompletedAtBetweenAndIdGreaterThanOrderByIdAsc(
        SettlementStatus status, LocalDateTime from, LocalDateTime to, Long afterId, Limit limit);

    Optional<LocalDateTime> findOldestCompletedAt(SettlementStatus status);

    long countBySellerIdAndStatusIn(Long sellerId, Collection<SettlementStatus> statuses);

    Optional<Settlement> findFirstByPeriodEndOnOrderByPeriodStartOnAsc(LocalDate periodEndOn);

    default Settlement getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    // 없거나 다른 판매자의 정산서면 404
    default Settlement getByIdAndSellerId(Long id, Long sellerId) {
        return findByIdAndSellerId(id, sellerId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
