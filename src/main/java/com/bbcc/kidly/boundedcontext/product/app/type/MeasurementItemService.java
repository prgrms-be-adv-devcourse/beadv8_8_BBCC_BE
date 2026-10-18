package com.bbcc.kidly.boundedcontext.product.app.type;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemResult;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeasurementItemService {

    private final MeasurementItemRepository measurementItemRepository;

    // 실측 항목을 만든다
    @Transactional
    public MeasurementItemResult create(MeasurementItemRequest request) {
        MeasurementItem item = measurementItemRepository.save(
            MeasurementItem.create(request.name(), request.code(), request.unit()));
        return new MeasurementItemResult(item.getId(), item.getName(), item.getCode(), item.getUnit());
    }

    // 지우지 않은 모든 실측 항목을 ID 순서로 돌려준다
    @Transactional(readOnly = true)
    public List<MeasurementItemResult> findAll() {
        List<MeasurementItemResult> results = new ArrayList<>();
        for (MeasurementItem item : measurementItemRepository.findAllByDeletedAtIsNullOrderByIdAsc()) {
            results.add(new MeasurementItemResult(item.getId(), item.getName(), item.getCode(), item.getUnit()));
        }
        return results;
    }
}
