package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductSaleStatusResult;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.Inventory;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductChangedData;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductSaleService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final EventPublisher eventPublisher;

    // 판매 시작이 상태를 바꿨을 때만 상품 정보 변경됨을 보낸다. 이미 판매중이면 같은 요청을 다시 받은 것이라 이벤트를 보내지 않는다.
    // 같은 상품에 요청이 동시에 오면 product.version 충돌로 하나만 저장되고(나머지는 409), 이벤트도 커밋된 트랜잭션에서만 나간다.
    @Transactional
    public ProductSaleStatusResult startSale(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
        Category category = categoryRepository.findById(product.getCategoryId())
            .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        ProductType productType = category.productTypeToRegister();

        boolean started = product.startSale(sellerId, category, productType);
        if (started) {
            ProductChangedEvent.Data data = ProductChangedData.of(
                product, productType, availableQtyByOptionId(product.getOptions()));
            eventPublisher.publish(new ProductChangedEvent(UUID.randomUUID(), LocalDateTime.now(), data));
            // 늘어난 version을 같은 트랜잭션에서 응답에 담으려고 바로 DB에 반영한다
            productRepository.saveAndFlush(product);
        }
        return new ProductSaleStatusResult(product.getId(), product.getStatus(), product.getVersion());
    }

    // 옵션들의 재고를 한 번에 읽어 옵션 ID별 판매 가능 수량으로 묶는다
    private Map<Long, Integer> availableQtyByOptionId(List<ProductOption> options) {
        List<Long> optionIds = new ArrayList<>();
        for (ProductOption option : options) {
            optionIds.add(option.getId());
        }
        Map<Long, Integer> availableQtyByOptionId = new HashMap<>();
        for (Inventory inventory : inventoryRepository.findAllOfOptions(optionIds)) {
            availableQtyByOptionId.put(inventory.getProductOptionId(), inventory.getAvailableQty());
        }
        return availableQtyByOptionId;
    }
}
