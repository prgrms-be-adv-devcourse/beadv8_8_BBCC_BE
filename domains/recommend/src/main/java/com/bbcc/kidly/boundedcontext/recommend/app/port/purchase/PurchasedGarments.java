package com.bbcc.kidly.boundedcontext.recommend.app.port.purchase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PurchasedGarments {

    /**
     * 자녀가 구매확정한 옷의 구매 기록을 구매확정 시각이 늦은 순서로 읽는다. 시각이 같으면 구매 기록이 늦게 생긴 순서다.
     */
    Page<PurchasedGarmentRow> findByChild(Long childProfileId, Pageable pageable);
}
