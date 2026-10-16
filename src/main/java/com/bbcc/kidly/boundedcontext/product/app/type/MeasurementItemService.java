package com.bbcc.kidly.boundedcontext.product.app.type;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemResult;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemRepository;
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
}
