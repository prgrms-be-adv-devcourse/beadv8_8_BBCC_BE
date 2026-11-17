package com.bbcc.kidly.boundedcontext.product.app.product.support;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductChangedData;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductChangedPublisher {

    private final InventoryRepository inventoryRepository;
    private final EventPublisher eventPublisher;

    public void publish(Product product, Category category) {
        List<Long> optionIds = product.getOptions().stream().map(ProductOption::getId).toList();
        Map<Long, Integer> availableQtyByOptionId = inventoryRepository.findAvailableQtyByOptionId(optionIds);
        ProductChangedEvent.Data data = ProductChangedData.of(product, category, availableQtyByOptionId);
        eventPublisher.publish(new ProductChangedEvent(UUID.randomUUID(), LocalDateTime.now(), data));
    }
}
