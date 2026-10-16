package com.bbcc.kidly.boundedcontext.product.domain.type;

public interface MeasurementItemRepository {

    // 실측 항목을 저장한다
    MeasurementItem save(MeasurementItem measurementItem);
}
