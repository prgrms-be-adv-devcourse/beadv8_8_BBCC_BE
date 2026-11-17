package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductSaleStatusResult;
import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductChangedPublisher;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductSaleService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductChangedPublisher productChangedPublisher;

    @Transactional
    public ProductSaleStatusResult changeSaleStatus(Long sellerId, Long productId, ProductStatus status) {
        var product = productRepository.getById(productId);
        Category category = findCategory(product);
        ProductType productType = category.productTypeToRegister();

        boolean changed = false;
        if (status == ProductStatus.ON_SALE) {
            changed = product.startSale(sellerId, category, productType, LocalDateTime.now());
        } else if (status == ProductStatus.STOPPED) {
            changed = product.stopSale(sellerId);
        }
        if (changed) {
            productRepository.saveAndFlush(product);
            productChangedPublisher.publish(product, category);
        }
        return toResult(product);
    }

    @Transactional
    public ProductSaleStatusResult changeSaleStatusByAdmin(Long productId, ProductStatus status) {
        var product = productRepository.getById(productId);
        Category category = findCategory(product);
        ProductType productType = category.productTypeToRegister();

        boolean changed = false;
        if (status == ProductStatus.ON_SALE) {
            changed = product.startSaleByAdmin(category, productType, LocalDateTime.now());
        } else if (status == ProductStatus.STOPPED) {
            changed = product.stopSaleByAdmin();
        }
        productRepository.saveAndFlush(product);
        if (changed) {
            productChangedPublisher.publish(product, category);
        }
        return toResult(product);
    }

    @Transactional
    public ProductSaleStatusResult delist(Long sellerId, Long productId) {
        var product = productRepository.getById(productId);
        if (product.delist(sellerId)) {
            productRepository.saveAndFlush(product);
            publishChanged(product);
        }
        return toResult(product);
    }

    @Transactional
    public ProductSaleStatusResult revertDelistByAdmin(Long productId) {
        var product = productRepository.getById(productId);
        product.revertDelistByAdmin();
        productRepository.saveAndFlush(product);
        publishChanged(product);
        return toResult(product);
    }

    private void publishChanged(Product product) {
        Category category = findCategory(product);
        productChangedPublisher.publish(product, category);
    }

    private Category findCategory(Product product) {
        return categoryRepository.getById(product.getCategoryId());
    }

    private ProductSaleStatusResult toResult(Product product) {
        return new ProductSaleStatusResult(product.getId(), product.getStatus(), product.getVersion());
    }
}
