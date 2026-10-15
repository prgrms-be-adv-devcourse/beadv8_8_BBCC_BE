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

/**
 * 판매자가 옵션의 재고를 증감량과 사유로 조정하는 유스케이스다.
 */
@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SoldOutPublisher soldOutPublisher;

    // 수량을 바꾼 경우에만 같은 트랜잭션에서 조정 기록을 한 행 남긴다 (INV-17).
    // 조건부 UPDATE가 영속성 컨텍스트를 비우므로 옵션은 그 전에 읽은 값만 쓴다
    @Transactional
    public InventoryAdjustmentResult adjust(long sellerId, long optionId, InventoryAdjustmentRequest request) {
        List<ProductOption> options = productRepository.findOptionsWithProduct(List.of(optionId));
        if (options.isEmpty()) {
            throw new BusinessException(ProductErrorCode.OPTION_NOT_FOUND);
        }
        ProductOption option = options.get(0);
        option.validateOwner(sellerId);
        InventoryAdjustmentReason reason = InventoryAdjustmentReason.valueOf(request.reason());
        int deltaQuantity = request.deltaQuantity();
        InventoryAdjustmentRecord record =
            InventoryAdjustmentRecord.adjust(new AdjustInfo(option, deltaQuantity, reason, sellerId));

        boolean adjusted = inventoryRepository.adjustIfAvailable(optionId, deltaQuantity, LocalDateTime.now());
        int availableQuantity = inventoryRepository.findAvailableQtyByOptionId(List.of(optionId)).get(optionId);
        // 판매자는 판매 가능 수량까지만 줄일 수 있다. 바뀐 행이 없으면 기록 없이 거절한다 (INV-03, INV-17)
        if (!adjusted) {
            throw new BusinessException(InventoryErrorCode.DECREASE_OVER_AVAILABLE,
                "판매 가능 수량보다 많이 줄일 수 없습니다. 지금 판매 가능 수량: " + availableQuantity);
        }
        inventoryRepository.saveAdjustmentRecord(record);
        // 품절 여부가 바뀌었으면 상품 정보 변경됨을 보낸다 (SALE-08). 재고 행을 바꾼 뒤에 상품 행을 잠가 재고 차감과 잠금 순서가 같다
        if (record.changesSoldOut(availableQuantity)) {
            soldOutPublisher.publishAfterAdjustment(optionId);
        }
        return new InventoryAdjustmentResult(optionId, reason.name(), deltaQuantity, availableQuantity);
    }
}
