package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;

public interface MeasurementItemRepository {

    // 실측 항목을 저장한다
    MeasurementItem save(MeasurementItem measurementItem);

    // ID로 실측 항목을 찾는다
    Optional<MeasurementItem> findById(Long id);

    // 지우지 않은 실측 항목만 ID 순서로 돌려준다. 선택지 조회에 쓴다 (TYPE-09)
    List<MeasurementItem> findAllByDeletedAtIsNullOrderByIdAsc();

    // 실측 항목을 ID로 찾는다. 없으면 404 오류(MEASUREMENT_ITEM_NOT_FOUND)를 던진다
    default MeasurementItem getById(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(MeasurementItemErrorCode.MEASUREMENT_ITEM_NOT_FOUND));
    }
}
