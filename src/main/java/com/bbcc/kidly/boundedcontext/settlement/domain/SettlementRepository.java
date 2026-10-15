package com.bbcc.kidly.boundedcontext.settlement.domain;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.util.Optional;

public interface SettlementRepository {

    Settlement save(Settlement settlement);

    Optional<Settlement> findById(Long id);

    Optional<Settlement> findByIdAndSellerId(Long id, Long sellerId);

    // 없거나 다른 판매자의 정산서면 404
    default Settlement getByIdAndSellerId(Long id, Long sellerId) {
        return findByIdAndSellerId(id, sellerId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
