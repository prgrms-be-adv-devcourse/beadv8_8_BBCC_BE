package com.bbcc.kidly.boundedcontext.product.app.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.dto.InventoryAdjustmentRequest;
import com.bbcc.kidly.boundedcontext.product.app.inventory.dto.InventoryAdjustmentResult;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentReason;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord.AdjustInfo;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자가 옵션의 재고를 증감량과 사유로 조정하는 유스케이스다.
 */
@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    // 수량을 바꾼 경우에만 같은 트랜잭션에서 조정 기록을 한 행 남긴다 (INV-17).
    // 조건부 UPDATE가 영속성 컨텍스트를 비우므로 옵션은 그 전에 읽은 값만 쓴다
    @Transactional
    public InventoryAdjustmentResult adjust(long sellerId, long optionId, InventoryAdjustmentRequest request) {
        ProductOption option = productRepository.findOptionsWithProduct(List.of(optionId)).get(0);
        InventoryAdjustmentReason reason = InventoryAdjustmentReason.valueOf(request.reason());
        int deltaQuantity = request.deltaQuantity();
        InventoryAdjustmentRecord record =
            InventoryAdjustmentRecord.adjust(new AdjustInfo(option, deltaQuantity, reason, sellerId));

        if (inventoryRepository.adjustIfAvailable(optionId, deltaQuantity, LocalDateTime.now())) {
            inventoryRepository.saveAdjustmentRecord(record);
        }
        int availableQuantity = inventoryRepository.findAvailableQtyByOptionId(List.of(optionId)).get(optionId);
        return new InventoryAdjustmentResult(optionId, reason.name(), deltaQuantity, availableQuantity);
    }
}
