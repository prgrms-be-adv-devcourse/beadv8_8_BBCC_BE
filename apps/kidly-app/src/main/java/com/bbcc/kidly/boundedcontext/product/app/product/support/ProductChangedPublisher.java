package com.bbcc.kidly.boundedcontext.product.app.product.support;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductChangedData;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 상품의 현재 모습과 옵션별 판매 가능 수량으로 상품 정보 변경됨을 만들어 발행한다.
 * 이벤트가 발행한 쪽의 커밋과 함께 나가도록, 호출한 쪽의 트랜잭션 안에서 불러야 한다.
 */
@Component
@RequiredArgsConstructor
public class ProductChangedPublisher {

    private final InventoryRepository inventoryRepository;
    private final EventPublisher eventPublisher;

    public void publish(Product product, ProductType productType) {
        List<Long> optionIds = new ArrayList<>();
        for (ProductOption option : product.getOptions()) {
            optionIds.add(option.getId());
        }
        Map<Long, Integer> availableQtyByOptionId = inventoryRepository.findAvailableQtyByOptionId(optionIds);
        ProductChangedEvent.Data data = ProductChangedData.of(product, productType, availableQtyByOptionId);
        eventPublisher.publish(new ProductChangedEvent(UUID.randomUUID(), LocalDateTime.now(), data));
    }
}
