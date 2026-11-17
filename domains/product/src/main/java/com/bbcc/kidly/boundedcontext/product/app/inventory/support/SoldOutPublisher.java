package com.bbcc.kidly.boundedcontext.product.app.inventory.support;

import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductChangedPublisher;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SoldOutPublisher {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductChangedPublisher productChangedPublisher;

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

    public void publishStartedSaleProducts(Collection<Long> optionIds) {
        for (Product product : productRepository.lockAllHavingOptions(optionIds)) {
            if (product.hasStartedSale()) {
                publishProductChanged(product);
            }
        }
    }

    private void publishProductChanged(Product product) {
        var category = categoryRepository.getById(product.getCategoryId());
        productChangedPublisher.publish(product, category);
    }
}
