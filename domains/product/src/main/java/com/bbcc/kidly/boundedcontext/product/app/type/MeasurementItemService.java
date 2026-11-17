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

    @Transactional
    public MeasurementItemResult create(MeasurementItemRequest request) {
        MeasurementItem item = measurementItemRepository.save(
            MeasurementItem.create(request.name(), request.code(), request.unit()));
        return new MeasurementItemResult(item.getId(), item.getName(), item.getCode(), item.getUnit());
    }

    @Transactional(readOnly = true)
    public List<MeasurementItemResult> findAll() {
        var results = new ArrayList<MeasurementItemResult>();
        for (MeasurementItem item : measurementItemRepository.findAllByDeletedAtIsNullOrderByIdAsc()) {
            results.add(new MeasurementItemResult(item.getId(), item.getName(), item.getCode(), item.getUnit()));
        }
        return results;
    }

    @Transactional
    public void delete(Long measurementItemId) {
        var item = measurementItemRepository.getByIdForUpdate(measurementItemId);
        boolean inUse = categoryRepository.existsByMeasurementItemsMeasurementItemAndDeletedAtIsNull(item);
        item.delete(LocalDateTime.now(), inUse);
    }
}
