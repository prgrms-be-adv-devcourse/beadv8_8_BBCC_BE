package com.bbcc.kidly.boundedcontext.product.app.type;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemResult;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeasurementItemService {

    private final MeasurementItemRepository measurementItemRepository;
    private final CategoryRepository categoryRepository;

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

    // 실측 항목을 지운다. 지우지 않은 카테고리가 필수로 연결했는지는 읽어 넘기고, 지울 수 있는지는 실측 항목이 판단한다 (TYPE-10)
    @Transactional
    public void delete(Long measurementItemId) {
        MeasurementItem item = measurementItemRepository.getById(measurementItemId);
        boolean inUse = categoryRepository.existsByMeasurementItemsMeasurementItemAndDeletedAtIsNull(item);
        item.delete(LocalDateTime.now(), inUse);
    }
}
