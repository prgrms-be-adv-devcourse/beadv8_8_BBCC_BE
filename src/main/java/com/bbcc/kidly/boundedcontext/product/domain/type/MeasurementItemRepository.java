package com.bbcc.kidly.boundedcontext.product.domain.type;

import java.util.List;

public interface MeasurementItemRepository {

    // 실측 항목을 저장한다
    MeasurementItem save(MeasurementItem measurementItem);

    // 모든 실측 항목을 ID 순서로 돌려준다
    List<MeasurementItem> findAll();
}
