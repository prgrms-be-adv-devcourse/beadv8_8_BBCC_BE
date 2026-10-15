package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductChangedPublisher;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 재고를 차감한 뒤 품절이 된 옵션이 있으면, 그 옵션이 속한 상품마다 상품 정보 변경됨을 발행한다.
 * 재고 차감 트랜잭션 안에서 불러야 한다. 차감이 커밋될 때만 상품 정보 변경됨이 나가게 하기 위해서다.
 */
@Component
@RequiredArgsConstructor
public class SoldOutPublisher {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductChangedPublisher productChangedPublisher;

    public void publish(InventoryDeductionRequest request) {
        List<Long> optionIds = new ArrayList<>();
        for (InventoryDeductionRequest.OptionQuantity quantity : request.optionQuantities()) {
            optionIds.add(quantity.optionId());
        }
        Map<Long, Integer> availableQtyByOptionId = inventoryRepository.findAvailableQtyByOptionId(optionIds);
        List<Long> soldOutOptionIds = request.soldOutOptionIds(availableQtyByOptionId);
        if (soldOutOptionIds.isEmpty()) {
            return;
        }
        for (Product product : productRepository.findAllHavingOptions(soldOutOptionIds)) {
            Category category = categoryRepository.findById(product.getCategoryId()).orElseThrow();
            productChangedPublisher.publish(product, category.getProductType());
        }
    }
}
