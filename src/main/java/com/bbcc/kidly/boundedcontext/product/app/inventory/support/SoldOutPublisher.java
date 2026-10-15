package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductChangedPublisher;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 재고를 차감하거나 판매자가 재고를 조정한 뒤 품절이 된 옵션이 있으면, 그 옵션이 속한 상품마다 상품 정보 변경됨을 발행한다.
 * 재고를 바꾸는 트랜잭션 안에서 불러야 한다. 재고 변경이 커밋될 때만 상품 정보 변경됨이 나가게 하기 위해서다.
 */
@Component
@RequiredArgsConstructor
public class SoldOutPublisher {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductChangedPublisher productChangedPublisher;

    // 같은 상품의 다른 옵션을 동시에 품절시키는 차감이 있으면, 늦은 쪽이 상품 행 잠금에서 앞선 차감이 커밋될 때까지 기다린다
    // ProductChangedPublisher가 잠금을 얻은 뒤 수량을 다시 읽고 발생 시각을 정하므로, 늦은 이벤트에 두 옵션의 품절이 모두 담긴다
    // 여기서 처음 읽는 수량은 품절된 옵션이 있는지 가리는 데만 쓴다
    public void publish(InventoryDeductionRequest request) {
        Map<Long, Integer> availableQtyByOptionId = inventoryRepository.findAvailableQtyByOptionId(request.optionIds());
        List<Long> soldOutOptionIds = request.soldOutOptionIds(availableQtyByOptionId);
        if (soldOutOptionIds.isEmpty()) {
            return;
        }
        for (Product product : productRepository.lockAllHavingOptions(soldOutOptionIds)) {
            publishProductChanged(product);
        }
    }

    // 판매자의 재고 조정으로 옵션의 품절 여부가 바뀐 뒤 그 옵션이 속한 상품의 상품 정보 변경됨을 발행한다
    // 품절 여부가 바뀌었는지는 부르는 쪽이 이미 판단했다 (SALE-08)
    // 판매를 시작하기 전 상품의 품절 여부는 판매를 시작할 때 나가는 상품 정보 변경됨에 담기므로 여기서는 보내지 않는다
    // 판매 상태는 상품 행을 잠근 뒤에 읽는다. 판매 시작과 동시에 와도 잠금이 풀린 뒤의 상태를 바르게 본다
    public void publishAfterAdjustment(long optionId) {
        for (Product product : productRepository.lockAllHavingOptions(List.of(optionId))) {
            if (product.hasStartedSale()) {
                publishProductChanged(product);
            }
        }
    }

    private void publishProductChanged(Product product) {
        Category category = categoryRepository.findById(product.getCategoryId()).orElseThrow();
        productChangedPublisher.publish(product, category.getProductType());
    }
}
