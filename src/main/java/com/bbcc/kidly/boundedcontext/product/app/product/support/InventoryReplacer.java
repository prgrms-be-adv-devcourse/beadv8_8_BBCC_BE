package com.bbcc.kidly.boundedcontext.product.app.product.support;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest.OptionInput;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentReason;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord.AdjustInfo;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 판매대기 상품의 옵션을 바꿔 넣은 뒤, 받은 옵션의 재고를 받은 초기 재고에 맞춘다. 새로 넣은 옵션은 재고를 열고,
 * 남긴 옵션은 초기 재고와 지금 판매 가능 수량의 차이만큼 정정한다. 호출한 쪽의 트랜잭션 안에서 불러야 한다.
 */
@Component
@RequiredArgsConstructor
public class InventoryReplacer {

    private final InventoryRepository inventoryRepository;

    /**
     * 판매대기 상품은 주문이 없어 판매 가능 수량이 곧 초기 재고에 정정을 더한 값이다. 그래서 받은 초기 재고와의 차이가
     * 정정할 수량이다. 옵션은 상품을 저장해 ID가 생긴 뒤의 것이어야 하고, 정정은 영속성 컨텍스트를 비우므로
     * 그 전에 재고를 여는 일과 기록을 만드는 일을 모두 끝낸다.
     *
     * @param requestedOptions 받은 옵션과 같은 순서로 짝지은 옵션
     * @param optionsBefore 바꿔 넣기 전의 옵션. 여기에 있던 객체가 아니면 새로 넣은 옵션이다
     * @param inputs 받은 옵션 입력. requestedOptions와 같은 순서다
     */
    public void align(List<ProductOption> requestedOptions, List<ProductOption> optionsBefore,
        List<OptionInput> inputs, Long sellerId) {
        Set<ProductOption> before = Collections.newSetFromMap(new IdentityHashMap<>());
        before.addAll(optionsBefore);
        List<ProductOption> kept = new ArrayList<>();
        for (ProductOption option : requestedOptions) {
            if (before.contains(option)) {
                kept.add(option);
            }
        }
        Map<Long, Integer> availableQty = inventoryRepository.findAvailableQtyByOptionId(idsOf(kept));
        List<Correction> corrections = new ArrayList<>();
        for (int i = 0; i < requestedOptions.size(); i++) {
            ProductOption option = requestedOptions.get(i);
            int initialQuantity = inputs.get(i).initialQuantity();
            if (!before.contains(option)) {
                inventoryRepository.saveOpening(InventoryOpening.open(option, initialQuantity, sellerId));
                continue;
            }
            int delta = initialQuantity - availableQty.getOrDefault(option.getId(), 0);
            if (delta != 0) {
                corrections.add(new Correction(option.getId(), delta, InventoryAdjustmentRecord.adjust(
                    new AdjustInfo(option, delta, InventoryAdjustmentReason.CORRECTION, sellerId))));
            }
        }
        applyCorrections(corrections);
    }

    // 만들어 둔 정정 기록과, 그 수량을 판매 가능 수량에 반영할 옵션 ID · 증감량이다
    private record Correction(Long optionId, int delta, InventoryAdjustmentRecord record) {
    }

    private void applyCorrections(List<Correction> corrections) {
        for (Correction correction : corrections) {
            boolean adjusted = inventoryRepository.adjustIfAvailable(
                correction.optionId(), correction.delta(), LocalDateTime.now());
            if (!adjusted) {
                throw new IllegalStateException("옵션 " + correction.optionId() + "의 재고를 정정하지 못했습니다");
            }
            inventoryRepository.saveAdjustmentRecord(correction.record());
        }
    }

    private List<Long> idsOf(List<ProductOption> options) {
        List<Long> ids = new ArrayList<>();
        for (ProductOption option : options) {
            ids.add(option.getId());
        }
        return ids;
    }
}
