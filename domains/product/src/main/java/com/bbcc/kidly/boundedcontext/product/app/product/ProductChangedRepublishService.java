package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductChangedPublisher;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductChangedRepublishService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductChangedPublisher productChangedPublisher;

    @Transactional
    public int republishStartedSaleProducts() {
        int publishedCount = 0;
        for (Product product : productRepository.findAll()) {
            if (!product.hasStartedSale()) {
                continue;
            }
            var category = categoryRepository.getById(product.getCategoryId());
            productChangedPublisher.publish(product, category);
            publishedCount++;
        }
        return publishedCount;
    }
}
