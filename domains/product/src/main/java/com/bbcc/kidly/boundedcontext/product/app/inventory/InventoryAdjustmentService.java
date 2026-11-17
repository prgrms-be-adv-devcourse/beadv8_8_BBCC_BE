package com.bbcc.kidly.boundedcontext.product.app.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.dto.InventoryAdjustmentRequest;
import com.bbcc.kidly.boundedcontext.product.app.inventory.dto.InventoryAdjustmentResult;
import com.bbcc.kidly.boundedcontext.product.app.inventory.support.SoldOutPublisher;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentReason;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord.AdjustInfo;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SoldOutPublisher soldOutPublisher;

    @Transactional
    public InventoryAdjustmentResult adjust(long sellerId, long optionId, InventoryAdjustmentRequest request) {
        List<ProductOption> options = productRepository.findOptionsWithProduct(List.of(optionId));
        if (options.isEmpty()) {
            throw new BusinessException(ProductErrorCode.OPTION_NOT_FOUND);
        }
        ProductOption option = options.get(0);
        option.validateAdjustableBy(sellerId);
        InventoryAdjustmentReason reason = InventoryAdjustmentReason.valueOf(request.reason());
        int deltaQuantity = request.deltaQuantity();
        InventoryAdjustmentRecord record =
            InventoryAdjustmentRecord.adjust(new AdjustInfo(option, deltaQuantity, reason, sellerId));

        boolean adjusted = inventoryRepository.adjustIfAvailable(optionId, deltaQuantity, LocalDateTime.now());
        int availableQuantity = inventoryRepository.findAvailableQtyByOptionId(List.of(optionId)).get(optionId);
        if (!adjusted) {
            throw new BusinessException(InventoryErrorCode.DECREASE_OVER_AVAILABLE,
                "판매 가능 수량보다 많이 줄일 수 없습니다. 지금 판매 가능 수량: " + availableQuantity);
        }
        inventoryRepository.saveAdjustmentRecord(record);
        if (record.changesSoldOut(availableQuantity)) {
            soldOutPublisher.publishStartedSaleProducts(List.of(optionId));
        }
        return new InventoryAdjustmentResult(optionId, reason.name(), deltaQuantity, availableQuantity);
    }
}
